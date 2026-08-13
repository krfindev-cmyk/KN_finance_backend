package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A manual cash movement not already captured by loan collections / supplier payments /
 * borrowing repayments — e.g. "Vegetable Sale", "Other Income", "Customer Refund", "Other Expense".
 * The Naveen cash ledger combines these with the automatic movements to get the day's full picture.
 */
@Entity
@Table(name = "naveen_cash_entries")
public class NaveenCashEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private NaveenCashDirection direction;

    /** Free-text label: Vegetable Sale, Other Income, Customer Refund, Other Expense, etc. */
    private String category;

    private Double amount;

    @Column(length = 1000)
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;

    public NaveenCashEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public NaveenCashDirection getDirection() { return direction; }
    public void setDirection(NaveenCashDirection direction) { this.direction = direction; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
