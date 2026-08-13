package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A part-payment made towards a supplier's outstanding balance. */
@Entity
@Table(name = "naveen_supplier_payments")
public class NaveenSupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long supplierId;
    private LocalDate date;
    private Double amount;

    @Column(length = 1000)
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;

    public NaveenSupplierPayment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
