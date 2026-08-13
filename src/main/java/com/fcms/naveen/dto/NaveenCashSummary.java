package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenCashEntry;

import java.time.LocalDate;
import java.util.List;

public class NaveenCashSummary {
    private LocalDate date;
    private double openingBalance;
    private double loanCollections;
    private double otherIncome;
    private double supplierPayments;
    private double borrowingRepayments;
    private double otherExpense;
    private double totalInflow;
    private double totalOutflow;
    private double closingBalance;
    private List<NaveenCashEntry> entries;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }
    public double getLoanCollections() { return loanCollections; }
    public void setLoanCollections(double loanCollections) { this.loanCollections = loanCollections; }
    public double getOtherIncome() { return otherIncome; }
    public void setOtherIncome(double otherIncome) { this.otherIncome = otherIncome; }
    public double getSupplierPayments() { return supplierPayments; }
    public void setSupplierPayments(double supplierPayments) { this.supplierPayments = supplierPayments; }
    public double getBorrowingRepayments() { return borrowingRepayments; }
    public void setBorrowingRepayments(double borrowingRepayments) { this.borrowingRepayments = borrowingRepayments; }
    public double getOtherExpense() { return otherExpense; }
    public void setOtherExpense(double otherExpense) { this.otherExpense = otherExpense; }
    public double getTotalInflow() { return totalInflow; }
    public void setTotalInflow(double totalInflow) { this.totalInflow = totalInflow; }
    public double getTotalOutflow() { return totalOutflow; }
    public void setTotalOutflow(double totalOutflow) { this.totalOutflow = totalOutflow; }
    public double getClosingBalance() { return closingBalance; }
    public void setClosingBalance(double closingBalance) { this.closingBalance = closingBalance; }
    public List<NaveenCashEntry> getEntries() { return entries; }
    public void setEntries(List<NaveenCashEntry> entries) { this.entries = entries; }
}
