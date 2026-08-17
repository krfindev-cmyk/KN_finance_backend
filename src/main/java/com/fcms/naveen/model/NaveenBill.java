package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An itemized vegetable purchase invoice — mirrors the physical "SRM Naveen Vegetables" bill
 * book: S.No / Item / Qty / Rate / Amount line items, with a grand total. Optionally tied to a
 * supplier so its total also counts toward that supplier's running balance.
 */
@Entity
@Table(name = "naveen_bills")
public class NaveenBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long supplierId;
    /** The NaveenSupplierPurchase created alongside this bill (if a supplier was chosen), so editing/deleting the bill keeps the supplier's balance in sync. */
    private Long purchaseId;
    private String customerName;
    private LocalDate date;
    private Double totalAmount;
    private String createdBy;
    private LocalDateTime createdAt;

    public NaveenBill() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
