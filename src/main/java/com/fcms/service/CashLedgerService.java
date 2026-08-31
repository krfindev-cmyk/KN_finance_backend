package com.fcms.service;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.model.CashDailyBalance;
import com.fcms.model.CashExpense;
import com.fcms.model.CashExpenseCategory;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.repository.CashDailyBalanceRepository;
import com.fcms.repository.CashExpenseRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Daily cash-in-hand ledger.
 *
 * Logic:
 *
 * Today's Balance After Spending
 * = Today's Collection - Today's Expenses
 *
 * Total Balance (Carried Forward)
 * = Yesterday's Closing Balance (exact, including negative)
 * + Today's Balance After Spending
 *
 * Negative balances carry forward automatically — if a day overspends, the shortfall rolls
 * into the next day's opening balance exactly as-is, with no reset to zero. If a stray old
 * entry or a one-off mistake throws the running balance off, use the "Set Balance" action
 * (pencil icon next to Total Balance (Carried Forward) in Cash Ledger) to manually correct a
 * specific day — that logs a visible Adjustment entry and every day after it recalculates
 * from that corrected point forward. This is the intended combination: automatic negative
 * carry-forward as the default math, with manual correction available as a one-time fix
 * whenever needed.
 */
@Service
public class CashLedgerService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CashExpenseRepository cashExpenseRepository;
    private final PaymentRepository paymentRepository;
    private final CashDailyBalanceRepository cashDailyBalanceRepository;

    public CashLedgerService(CashExpenseRepository cashExpenseRepository, PaymentRepository paymentRepository,
                              CashDailyBalanceRepository cashDailyBalanceRepository) {
        this.cashExpenseRepository = cashExpenseRepository;
        this.paymentRepository = paymentRepository;
        this.cashDailyBalanceRepository = cashDailyBalanceRepository;
    }

    /**
     * Upserts the full stored ledger row for one day: Collected Today, Spent Today, Today's
     * Balance After Spending, and Total Balance (Carried Forward) — this is what makes
     * carry-forward permanent and gives every figure on the Cash Ledger page a database row.
     */
    private void storeDailyLedger(LocalDate date, double collectedToday, double spentToday,
                                   double balanceAfterSpending, double totalBalanceCarriedForward) {
        CashDailyBalance row = cashDailyBalanceRepository.findByDate(date).orElseGet(CashDailyBalance::new);
        row.setDate(date);
        row.setCollectedToday(round2(collectedToday));
        row.setSpentToday(round2(spentToday));
        row.setBalanceAfterSpending(round2(balanceAfterSpending));
        row.setTotalBalanceCarriedForward(round2(totalBalanceCarriedForward));
        row.setUpdatedAt(LocalDateTime.now());
        cashDailyBalanceRepository.save(row);
    }

    /** Total cash actually collected on a given date (NotPaid entries contribute nothing). */
    private double collectedOn(LocalDate date) {
        return paymentRepository.findByDate(date).stream()
                .filter(p -> p.getType() != PaymentType.NotPaid)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    /**
     * Calculates yesterday's actual closing balance. If yesterday's balance has already been
     * stored in the database (either because it was computed before, or because it was set
     * manually via setBalance), that stored value is used directly — this is what makes a
     * manual correction on one day permanently anchor every later day, instead of being
     * recomputed from the entire transaction history (and any old stray entries in it) every
     * single time. Only when no stored value exists yet does it fall back to walking forward
     * day-by-day from the very first transaction. The running balance is NOT clamped to zero —
     * a negative day carries its exact shortfall into the next day's opening balance.
     *
     * Example:
     * Day 1: Collection 0, Expense 500 -> Closing -500
     * Day 2: Previous balance -500 (carried through, not reset). Collection 2000, Expense 500 -> Closing 1000
     * Day 3: Previous balance 1000. Collection 1000, Expense 500 -> Closing 1500
     */
    private double calculateYesterdayBalance(LocalDate date) {
        LocalDate yesterday = date.minusDays(1);

        Optional<CashDailyBalance> stored = cashDailyBalanceRepository.findByDate(yesterday);
        if (stored.isPresent()) {
            Double v = stored.get().getTotalBalanceCarriedForward();
            return v == null ? 0 : v;
        }

        List<Payment> payments = paymentRepository.findAll();
        List<CashExpense> expenses = cashExpenseRepository.findByDateBefore(date);

        LocalDate firstDate = null;
        for (Payment payment : payments) {
            if (payment.getDate() == null || payment.getDate().isAfter(yesterday)) continue;
            if (payment.getType() == PaymentType.NotPaid) continue;
            if (firstDate == null || payment.getDate().isBefore(firstDate)) firstDate = payment.getDate();
        }
        for (CashExpense expense : expenses) {
            if (expense.getDate() == null || expense.getDate().isAfter(yesterday)) continue;
            if (firstDate == null || expense.getDate().isBefore(firstDate)) firstDate = expense.getDate();
        }

        if (firstDate == null) {
            return 0;
        }

        double balance = 0;
        LocalDate currentDate = firstDate;

        while (!currentDate.isAfter(yesterday)) {
            final LocalDate processingDate = currentDate;

            double collected = payments.stream()
                    .filter(p -> p.getDate() != null && p.getDate().equals(processingDate))
                    .filter(p -> p.getType() != PaymentType.NotPaid)
                    .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                    .sum();

            double spent = expenses.stream()
                    .filter(e -> e.getDate() != null && e.getDate().equals(processingDate))
                    .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount())
                    .sum();

            balance = round2(balance + collected - spent);
            currentDate = currentDate.plusDays(1);
        }

        return round2(balance);
    }

    /**
     * The cash-in-hand summary for one day: opening balance (yesterday's exact closing balance,
     * negative or positive), today's collection, today's expenses, and the resulting closing
     * balance — which becomes tomorrow's opening balance automatically.
     */
    public CashLedgerSummary summary(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(IST);

        double yesterdayBalance = calculateYesterdayBalance(d);
        double openingBalance = round2(yesterdayBalance);

        double collectedToday = round2(collectedOn(d));
        List<CashExpense> expenses = cashExpenseRepository.findByDateOrderByCreatedAtDesc(d);
        double expensesToday = round2(expenses.stream().mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());
        double balanceAfterSpending = round2(collectedToday - expensesToday);
        double closingBalance = round2(openingBalance + balanceAfterSpending);
        storeDailyLedger(d, collectedToday, expensesToday, balanceAfterSpending, closingBalance);

        CashLedgerSummary summary = new CashLedgerSummary();
        summary.setDate(d);
        summary.setOpeningBalance(openingBalance);
        summary.setCollectedToday(collectedToday);
        summary.setExpensesToday(expensesToday);
        summary.setClosingBalance(closingBalance);
        summary.setExpenses(expenses);
        return summary;
    }

    public CashExpense addExpense(CashExpense expense, String createdBy) {
        if (expense.getDate() == null) expense.setDate(LocalDate.now(IST));
        expense.setCreatedBy(createdBy);
        expense.setCreatedAt(LocalDateTime.now());
        return cashExpenseRepository.save(expense);
    }

    public void deleteExpense(Long id) {
        cashExpenseRepository.deleteById(id);
    }

    /** Updates an existing entry's fields in place (amount, category, recipient, mode, notes, date) — the entry's id/createdBy/createdAt stay as originally recorded. */
    public CashExpense updateExpense(Long id, CashExpense updated) {
        CashExpense existing = cashExpenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cash entry not found: " + id));
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        if (updated.getAmount() != null) existing.setAmount(updated.getAmount());
        if (updated.getCategory() != null) existing.setCategory(updated.getCategory());
        existing.setRecipientName(updated.getRecipientName());
        existing.setSentVia(updated.getSentVia());
        existing.setNotes(updated.getNotes());
        return cashExpenseRepository.save(existing);
    }

    /**
     * Directly sets the Total Balance (Carried Forward) for a given day to whatever the admin
     * types in — e.g. correcting it to match cash actually counted in hand. Under the hood this
     * logs a single "Adjustment" entry for the difference (visible in the entry list like any
     * other expense, so there's a clear audit trail of who changed it and by how much), then
     * re-runs summary() so the resulting closing balance — now equal to targetBalance — gets
     * saved into the cash_daily_balances table. That stored value becomes the fixed anchor every
     * later day carries forward from.
     */
    public CashLedgerSummary setBalance(LocalDate date, double targetBalance, String editedBy) {
        LocalDate d = date != null ? date : LocalDate.now(IST);
        CashLedgerSummary current = summary(d);
        double delta = targetBalance - current.getClosingBalance();
        if (Math.abs(delta) >= 0.005) {
            CashExpense adjustment = new CashExpense();
            adjustment.setDate(d);
            adjustment.setCategory(CashExpenseCategory.Adjustment);
            // An expense amount subtracts from the balance, so to raise the balance by `delta`
            // the logged amount must be the negative of that.
            adjustment.setAmount(round2(-delta));
            adjustment.setNotes("Manual balance correction to " + round2(targetBalance));
            addExpense(adjustment, editedBy);
        }
        return summary(d);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
