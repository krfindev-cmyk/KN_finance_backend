package com.fcms.naveen.dto;

public class NaveenDashboard {
    private double cashAvailable;
    private double moneyToReceive;
    private double moneyToPay;
    private double supplierBalance;
    private int activeLoans;
    private int activeBorrowings;
    private int activeSuppliers;
    private double totalAmount;
    private double totalPaid;
    private double totalPending;

    public double getCashAvailable() { return cashAvailable; }
    public void setCashAvailable(double cashAvailable) { this.cashAvailable = cashAvailable; }
    public double getMoneyToReceive() { return moneyToReceive; }
    public void setMoneyToReceive(double moneyToReceive) { this.moneyToReceive = moneyToReceive; }
    public double getMoneyToPay() { return moneyToPay; }
    public void setMoneyToPay(double moneyToPay) { this.moneyToPay = moneyToPay; }
    public double getSupplierBalance() { return supplierBalance; }
    public void setSupplierBalance(double supplierBalance) { this.supplierBalance = supplierBalance; }
    public int getActiveLoans() { return activeLoans; }
    public void setActiveLoans(int activeLoans) { this.activeLoans = activeLoans; }
    public int getActiveBorrowings() { return activeBorrowings; }
    public void setActiveBorrowings(int activeBorrowings) { this.activeBorrowings = activeBorrowings; }
    public int getActiveSuppliers() { return activeSuppliers; }
    public void setActiveSuppliers(int activeSuppliers) { this.activeSuppliers = activeSuppliers; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(double totalPaid) { this.totalPaid = totalPaid; }
    public double getTotalPending() { return totalPending; }
    public void setTotalPending(double totalPending) { this.totalPending = totalPending; }
}
