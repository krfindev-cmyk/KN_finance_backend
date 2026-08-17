package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenBill;
import com.fcms.naveen.model.NaveenBillItem;

import java.util.List;

public class BillDetail {
    private NaveenBill bill;
    private List<NaveenBillItem> items;
    private String supplierName;

    public NaveenBill getBill() { return bill; }
    public void setBill(NaveenBill bill) { this.bill = bill; }
    public List<NaveenBillItem> getItems() { return items; }
    public void setItems(List<NaveenBillItem> items) { this.items = items; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
}
