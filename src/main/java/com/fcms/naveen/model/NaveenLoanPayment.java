package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One collection recorded against a NaveenLoan. */
@Entity
@Table(name = "naveen_loan_payments")
public class NaveenLoanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long loanId;
    private LocalDate date;
    private Double amount;

    @Enumerated(EnumType.STRING)
    private NaveenPaymentType type;

    private String collectedBy;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    public NaveenLoanPayment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public NaveenPaymentType getType() { return type; }
    public void setType(NaveenPaymentType type) { this.type = type; }
    public String getCollectedBy() { return collectedBy; }
    public void setCollectedBy(String collectedBy) { this.collectedBy = collectedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
