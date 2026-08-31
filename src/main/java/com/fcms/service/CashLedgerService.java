package com.fcms.service;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.model.CashDailyBalance;
import com.fcms.model.CashExpense;
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
 * + Manual Adjustment for today, if one was set (see below)
 *
 * Negative balances carry forward automatically — if a day overspends, the shortfall rolls
 * into the next day's opening balance exactly as-is, with no reset to zero.
 *
 * Manual correction: the "Set Balance" pencil icon next to Total Balance (Carried Forward) in
 * Cash Ledger lets an admin type in the correct value for one day. This is stored as a separate
 * `manualAdjustment` number on that day's row in cash_daily_balances — it is NOT logged as a
 * cash_expenses entry. That's deliberate: a cash_expenses entry would (a) show up in and inflate
 * "Spent / Sent Today", which is supposed to reflect real spending only, and (b) be deletable
 * from the entries list, which would silently undo the correction. Keeping it as its own field
 * means Collected Today / Spent Today always reflect real transactions, the correction survives
 * any edits to the entries list, and every later day still carries forward the corrected total.
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
     * Balance After Spending, and Total Balance (Carried Forward). The manualAdjustment field is
     * written back as-is (not reset) so a correction set via setBalance() survives every normal
     * page load / recompute.
     */
    private void storeDailyLedger(LocalDate date, double collectedToday, double spentToday,
                                   double balanceAfterSpending, double totalBalanceCarriedForward,
                                   double manualAdjustment) {
        CashDailyBalance row = cashDailyBalanceRepository.findByDate(date).orElseGet(CashDailyBalance::new);
        row.setDate(date);
        row.setCollectedToday(round2(collectedToday));
        row.setSpentToday(round2(spentToday));
        row.setBalanceAfterSpending(round2(balanceAfterSpending));
        row.setTotalBalanceCarriedForward(round2(totalBalanceCarriedForward));
        row.setManualAdjustment(round2(manualAdjustment));
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

    /** The manual correction stored for a day, or 0 if none has been set. */
    private double manualAdjustmentOn(LocalDate date) {
        return cashDailyBalanceRepository.findByDate(date)
                .map(CashDailyBalance::getManualAdjustment)
                .map(v -> v == null ? 0.0 : v)
                .orElse(0.0);
    }

    /**
     * Calculates yesterday's actual closing balance. If yesterday's balance has already been
     * stored in the database, that stored value (which already includes any manual adjustment)
     * is used directly — this is what makes a manual correction permanently anchor every later
     * day, instead of being recomputed from the entire transaction history every time. Only when
     * no stored value exists yet does it fall back to walking forward day-by-day from the very
     * first transaction. The running balance is NOT clamped to zero — a negative day carries its
     * exact shortfall into the next day's opening balance.
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

            balance = round2(balance + collected - spent + manualAdjustmentOn(processingDate));
            currentDate = currentDate.plusDays(1);
        }

        return round2(balance);
    }

    /**
     * The cash-in-hand summary for one day: opening balance (yesterday's exact closing balance,
     * negative or positive), today's real collection/spending, and the resulting closing balance
     * — opening + today's balance after spending + any manual correction set for today — which
     * becomes tomorrow's opening balance automatically.
     */
    public CashLedgerSummary summary(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(IST);

        double openingBalance = round2(calculateYesterdayBalance(d));

        double collectedToday = round2(collectedOn(d));
        List<CashExpense> expenses = cashExpenseRepository.findByDateOrderByCreatedAtDesc(d);
        double expensesToday = round2(expenses.stream().mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());
        double balanceAfterSpending = round2(collectedToday - expensesToday);

        double manualAdjustment = manualAdjustmentOn(d);
        double closingBalance = round2(openingBalance + balanceAfterSpending + manualAdjustment);
        storeDailyLedger(d, collectedToday, expensesToday, balanceAfterSpending, closingBalance, manualAdjustment);

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
     * types in. Works out what correction (manualAdjustment) needs to be added on top of the
     * naturally-computed balance to reach that target, and stores it on that day's row — separate
     * from cash_expenses entirely, so it never appears in or affects Collected Today / Spent
     * Today, and can't be undone by deleting or editing an unrelated entry. Every later day's
     * carried-forward balance is computed from this corrected total automatically.
     */
    public CashLedgerSummary setBalance(LocalDate date, double targetBalance, String editedBy) {
        LocalDate d = date != null ? date : LocalDate.now(IST);

        // Compute what the balance would naturally be with no correction applied.
        double openingBalance = round2(calculateYesterdayBalance(d));
        double collectedToday = round2(collectedOn(d));
        double expensesToday = round2(cashExpenseRepository.findByDateOrderByCreatedAtDesc(d).stream()
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());
        double balanceAfterSpending = round2(collectedToday - expensesToday);
        double naturalClosing = round2(openingBalance + balanceAfterSpending);

        double manualAdjustment = round2(targetBalance - naturalClosing);
        storeDailyLedger(d, collectedToday, expensesToday, balanceAfterSpending,
                round2(naturalClosing + manualAdjustment), manualAdjustment);

        return summary(d);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
