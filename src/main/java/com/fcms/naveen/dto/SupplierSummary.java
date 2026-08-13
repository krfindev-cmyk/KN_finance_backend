package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenSupplier;
import com.fcms.naveen.model.NaveenSupplierPayment;
import com.fcms.naveen.model.NaveenSupplierPurchase;

import java.util.List;

public class SupplierSummary {
    private NaveenSupplier supplier;
    private double totalPurchases;
    private double totalPaid;
    private double balance;
    private List<NaveenSupplierPurchase> purchases;
    private List<NaveenSupplierPayment> payments;

    public NaveenSupplier getSupplier() { return supplier; }
    public void setSupplier(NaveenSupplier supplier) { this.supplier = supplier; }
    public double getTotalPurchases() { return totalPurchases; }
    public void setTotalPurchases(double totalPurchases) { this.totalPurchases = totalPurchases; }
    public double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(double totalPaid) { this.totalPaid = totalPaid; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public List<NaveenSupplierPurchase> getPurchases() { return purchases; }
    public void setPurchases(List<NaveenSupplierPurchase> purchases) { this.purchases = purchases; }
    public List<NaveenSupplierPayment> getPayments() { return payments; }
    public void setPayments(List<NaveenSupplierPayment> payments) { this.payments = payments; }
}
