package com.fcms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One outflow entry against a day's collected cash — the owner tells the admin to send money
 * to someone, or the admin spends on petrol/food/salary out of what was collected that day.
 * The running cash-in-hand balance carries forward day to day (see CashLedgerService).
 */
@Entity
@Table(name = "cash_expenses")
public class CashExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;
    private Double amount;

    @Enumerated(EnumType.STRING)
    private CashExpenseCategory category;

    /** Who received the money (person's name) — used for Salary / SentToPerson entries. */
    private String recipientName;

    /** How it was sent: Cash, GPay, PhonePe, Bank Transfer, etc. */
    private String sentVia;

    @Column(length = 1000)
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;

    public CashExpense() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public CashExpenseCategory getCategory() { return category; }
    public void setCategory(CashExpenseCategory category) { this.category = category; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getSentVia() { return sentVia; }
    public void setSentVia(String sentVia) { this.sentVia = sentVia; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
