package com.fcms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The stored Total Balance (Carried Forward) — i.e. the closing cash-in-hand balance — for one
 * specific day. One row per date. Once a day's balance is stored here, it becomes the fixed
 * starting point for every later day's calculation instead of being re-derived from scratch each
 * time, so a manual correction (via CashLedgerService.setBalance) sticks permanently.
 */
@Entity
@Table(name = "cash_daily_balances", uniqueConstraints = @UniqueConstraint(columnNames = "ledger_date"))
public class CashDailyBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ledger_date", nullable = false)
    private LocalDate date;

    private Double closingBalance;

    private LocalDateTime updatedAt;

    public CashDailyBalance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getClosingBalance() { return closingBalance; }
    public void setClosingBalance(Double closingBalance) { this.closingBalance = closingBalance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
