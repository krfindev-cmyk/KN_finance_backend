package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenBillItem;

import java.time.LocalDate;
import java.util.List;

/** Payload for creating/updating a bill: an optional supplier, the invoice date, and its line items. */
public class BillRequest {
    private Long supplierId;
    private String customerName;
    private LocalDate date;
    private List<NaveenBillItem> items;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public List<NaveenBillItem> getItems() { return items; }
    public void setItems(List<NaveenBillItem> items) { this.items = items; }
}
