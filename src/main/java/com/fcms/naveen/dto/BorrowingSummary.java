package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenBorrowing;
import com.fcms.naveen.model.NaveenBorrowingRepayment;

import java.util.List;

public class BorrowingSummary {
    private NaveenBorrowing borrowing;
    private double totalPayable;
    private double totalRepaid;
    private double balance;
    private List<NaveenBorrowingRepayment> repayments;

    public NaveenBorrowing getBorrowing() { return borrowing; }
    public void setBorrowing(NaveenBorrowing borrowing) { this.borrowing = borrowing; }
    public double getTotalPayable() { return totalPayable; }
    public void setTotalPayable(double totalPayable) { this.totalPayable = totalPayable; }
    public double getTotalRepaid() { return totalRepaid; }
    public void setTotalRepaid(double totalRepaid) { this.totalRepaid = totalRepaid; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public List<NaveenBorrowingRepayment> getRepayments() { return repayments; }
    public void setRepayments(List<NaveenBorrowingRepayment> repayments) { this.repayments = repayments; }
}
