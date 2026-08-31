package com.fcms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The stored daily cash ledger row — one per date, holding every figure the Cash Ledger page
 * shows: Collected Today, Spent / Sent Today, Today's Balance After Spending, and Total Balance
 * (Carried Forward). These are filled in automatically from the payments and cash_expenses
 * tables whenever CashLedgerService.summary() runs for that date — this table is not edited
 * directly by hand, except for totalBalanceCarriedForward via the "Set Balance" pencil icon,
 * which anchors that one field and lets every later day carry forward from it.
 */
@Entity
@Table(name = "cash_daily_balances", uniqueConstraints = @UniqueConstraint(columnNames = "ledger_date"))
public class CashDailyBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_date", nullable = false)
    private LocalDate date;

    /** Total cash collected that day (sum of payments, NotPaid excluded). */
    private Double collectedToday;

    /** Total spent/sent out that day (sum of cash_expenses, including any Adjustment entries). */
    private Double spentToday;

    /** Collected Today - Spent Today, for that day alone (no carry-forward involved). */
    private Double balanceAfterSpending;

    /** Yesterday's totalBalanceCarriedForward + this day's balanceAfterSpending + manualAdjustment. Becomes tomorrow's opening balance. */
    private Double totalBalanceCarriedForward;

    /**
     * A manual correction applied on top of the naturally-computed balance for this day only,
     * set via the "Set Balance" pencil icon. Kept separate from cash_expenses entirely, so it
     * never shows up in or affects Collected Today / Spent Today, and can't be undone by
     * deleting or editing an unrelated expense entry. Defaults to 0 (no correction).
     */
    private Double manualAdjustment;

    private LocalDateTime updatedAt;

    public CashDailyBalance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getCollectedToday() { return collectedToday; }
    public void setCollectedToday(Double collectedToday) { this.collectedToday = collectedToday; }
    public Double getSpentToday() { return spentToday; }
    public void setSpentToday(Double spentToday) { this.spentToday = spentToday; }
    public Double getBalanceAfterSpending() { return balanceAfterSpending; }
    public void setBalanceAfterSpending(Double balanceAfterSpending) { this.balanceAfterSpending = balanceAfterSpending; }
    public Double getTotalBalanceCarriedForward() { return totalBalanceCarriedForward; }
    public void setTotalBalanceCarriedForward(Double totalBalanceCarriedForward) { this.totalBalanceCarriedForward = totalBalanceCarriedForward; }
    public Double getManualAdjustment() { return manualAdjustment; }
    public void setManualAdjustment(Double manualAdjustment) { this.manualAdjustment = manualAdjustment; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
