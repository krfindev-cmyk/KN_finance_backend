package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One stock purchase from a supplier (e.g. 500 kg Tomato @ Rs.30/kg = Rs.15,000), settled later in part-payments. */
@Entity
@Table(name = "naveen_supplier_purchases")
public class NaveenSupplierPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long supplierId;
    private LocalDate date;
    private String item;
    private Double qty;
    private Double rate;
    private Double amount;

    @Column(length = 1000)
    private String notes;

    private String createdBy;
    private LocalDateTime createdAt;

    public NaveenSupplierPurchase() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public Double getQty() { return qty; }
    public void setQty(Double qty) { this.qty = qty; }
    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
