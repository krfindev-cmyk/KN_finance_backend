package com.fcms.service;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.model.CashExpense;
import com.fcms.model.CashExpenseCategory;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.repository.CashExpenseRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily cash-in-hand ledger: how much was collected that day, what the admin spent/sent out of
 * it (petrol/food allowance, salary, money sent to someone on the owner's instruction), and the
 * balance left over. Whatever isn't spent on a given day automatically carries forward as the
 * next day's opening balance — nothing needs to be manually rolled over.
 */
@Service
public class CashLedgerService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CashExpenseRepository cashExpenseRepository;
    private final PaymentRepository paymentRepository;

    public CashLedgerService(CashExpenseRepository cashExpenseRepository, PaymentRepository paymentRepository) {
        this.cashExpenseRepository = cashExpenseRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Total cash actually collected on a given date (NotPaid entries contribute nothing). */
    private double collectedOn(LocalDate date) {
        return paymentRepository.findByDate(date).stream()
                .filter(p -> p.getType() != PaymentType.NotPaid)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    private double collectedBefore(LocalDate date) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getDate() != null && p.getDate().isBefore(date))
                .filter(p -> p.getType() != PaymentType.NotPaid)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    private double expensesOn(LocalDate date) {
        return cashExpenseRepository.findByDateOrderByCreatedAtDesc(date).stream()
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount())
                .sum();
    }

    private double expensesBefore(LocalDate date) {
        return cashExpenseRepository.findByDateBefore(date).stream()
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount())
                .sum();
    }

    /**
     * The cash-in-hand summary for one day: opening balance (everything collected minus
     * everything spent before this date), today's collection, today's expenses, and the
     * resulting closing balance — which becomes tomorrow's opening balance automatically.
     */
    public CashLedgerSummary summary(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(IST);

        double openingBalance = round2(collectedBefore(d) - expensesBefore(d));
        double collectedToday = round2(collectedOn(d));
        List<CashExpense> expenses = cashExpenseRepository.findByDateOrderByCreatedAtDesc(d);
        double expensesToday = round2(expenses.stream().mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());
        double closingBalance = round2(openingBalance + collectedToday - expensesToday);

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
     * other expense, so there's a clear audit trail of who changed it and by how much) rather
     * than silently overwriting anything, since the balance itself isn't a stored field — it's
     * always recomputed from collections minus expenses.
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
