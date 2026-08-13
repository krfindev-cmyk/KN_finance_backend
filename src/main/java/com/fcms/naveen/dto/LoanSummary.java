package com.fcms.naveen.dto;

import com.fcms.naveen.model.NaveenLoan;
import com.fcms.naveen.model.NaveenLoanPayment;

import java.util.List;

public class LoanSummary {
    private NaveenLoan loan;
    private List<NaveenLoanPayment> payments;

    public NaveenLoan getLoan() { return loan; }
    public void setLoan(NaveenLoan loan) { this.loan = loan; }
    public List<NaveenLoanPayment> getPayments() { return payments; }
    public void setPayments(List<NaveenLoanPayment> payments) { this.payments = payments; }
}
