package com.fcms.naveen.model;

import jakarta.persistence.*;

/** One line item on a NaveenBill: S.No is just this item's position in the list. */
@Entity
@Table(name = "naveen_bill_items")
public class NaveenBillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long billId;
    private String item;
    private Double qty;
    private Double rate;
    private Double amount;

    public NaveenBillItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public Double getQty() { return qty; }
    public void setQty(Double qty) { this.qty = qty; }
    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
