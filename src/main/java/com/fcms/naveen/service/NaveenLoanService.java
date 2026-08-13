package com.fcms.naveen.service;

import com.fcms.naveen.dto.LoanSummary;
import com.fcms.naveen.model.*;
import com.fcms.naveen.repository.NaveenLoanPaymentRepository;
import com.fcms.naveen.repository.NaveenLoanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Money Naveen has given out (receivables) with a daily/weekly/monthly/custom collection schedule. */
@Service
public class NaveenLoanService {

    private final NaveenLoanRepository loanRepository;
    private final NaveenLoanPaymentRepository paymentRepository;

    public NaveenLoanService(NaveenLoanRepository loanRepository, NaveenLoanPaymentRepository paymentRepository) {
        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
    }

    public NaveenLoan addLoan(NaveenLoan loan) {
        if (loan.getTotalInstallments() == null || loan.getTotalInstallments() <= 0) loan.setTotalInstallments(100);
        if ((loan.getInstallmentAmount() == null || loan.getInstallmentAmount() <= 0) && loan.getAmount() != null) {
            loan.setInstallmentAmount(Math.round((loan.getAmount() / loan.getTotalInstallments()) * 100.0) / 100.0);
        }
        loan.setPaidInstallments(0);
        loan.setTotalPaid(0.0);
        loan.setPendingAmount(loan.getAmount());
        loan.setNextDueDate(loan.getDate());
        loan.setStatus(NaveenLoanStatus.Running);
        if (loan.getGroupKey() == null || loan.getGroupKey().isBlank()) {
            loan.setGroupKey(loan.getBorrowerName() == null ? null : loan.getBorrowerName().trim().toLowerCase());
        }
        loan.setCreatedAt(LocalDateTime.now());
        return loanRepository.save(loan);
    }

    public List<LoanSummary> listAll() {
        return loanRepository.findAll().stream()
                .map(this::summarize)
                .sorted(Comparator.comparing((LoanSummary s) -> s.getLoan().getPendingAmount() == null ? 0 : s.getLoan().getPendingAmount()).reversed())
                .toList();
    }

    public LoanSummary getOne(Long loanId) {
        NaveenLoan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        return summarize(loan);
    }

    private LoanSummary summarize(NaveenLoan loan) {
        LoanSummary summary = new LoanSummary();
        summary.setLoan(loan);
        summary.setPayments(paymentRepository.findByLoanIdOrderByDateDesc(loan.getId()));
        return summary;
    }

    /**
     * Records one collection against a loan. Only one marking per date is kept: an existing
     * marking for the same day is reversed and replaced, mirroring the main KR Finance behaviour.
     */
    public NaveenLoanPayment recordPayment(NaveenLoanPayment payment) {
        NaveenLoan loan = loanRepository.findById(payment.getLoanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        List<NaveenLoanPayment> existing = payment.getDate() == null
                ? List.of()
                : paymentRepository.findByLoanIdAndDate(payment.getLoanId(), payment.getDate());
        for (NaveenLoanPayment old : existing) reverse(loan, old);
        if (!existing.isEmpty()) paymentRepository.deleteAll(existing);

        payment.setCreatedAt(LocalDateTime.now());
        apply(loan, payment);
        recompute(loan);
        loanRepository.save(loan);
        return paymentRepository.save(payment);
    }

    private void apply(NaveenLoan loan, NaveenLoanPayment payment) {
        double amt = payment.getAmount() == null ? 0 : payment.getAmount();
        if (payment.getType() == NaveenPaymentType.Paid || payment.getType() == NaveenPaymentType.Partial
                || payment.getType() == NaveenPaymentType.Advance) {
            loan.setTotalPaid((loan.getTotalPaid() == null ? 0 : loan.getTotalPaid()) + amt);
            if (payment.getType() == NaveenPaymentType.Paid) {
                loan.setPaidInstallments((loan.getPaidInstallments() == null ? 0 : loan.getPaidInstallments()) + 1);
                loan.setNextDueDate(nextDate(payment.getDate(), loan.getFrequency(), 1));
            } else if (payment.getType() == NaveenPaymentType.Advance) {
                double inst = loan.getInstallmentAmount() == null ? 0 : loan.getInstallmentAmount();
                int periods = inst > 0 ? Math.max(1, (int) Math.floor(amt / inst)) : 1;
                loan.setPaidInstallments((loan.getPaidInstallments() == null ? 0 : loan.getPaidInstallments()) + periods);
                loan.setNextDueDate(nextDate(payment.getDate(), loan.getFrequency(), periods));
            }
            // Partial: due date unchanged, still counts as pending toward the next slot.
        }
    }

    private void reverse(NaveenLoan loan, NaveenLoanPayment payment) {
        double amt = payment.getAmount() == null ? 0 : payment.getAmount();
        boolean counted = payment.getType() == NaveenPaymentType.Paid || payment.getType() == NaveenPaymentType.Partial
                || payment.getType() == NaveenPaymentType.Advance;
        if (counted) {
            double totalPaid = loan.getTotalPaid() == null ? 0 : loan.getTotalPaid();
            loan.setTotalPaid(Math.max(0, totalPaid - amt));
        }
        if (payment.getType() == NaveenPaymentType.Paid) {
            loan.setPaidInstallments(Math.max(0, (loan.getPaidInstallments() == null ? 0 : loan.getPaidInstallments()) - 1));
        } else if (payment.getType() == NaveenPaymentType.Advance) {
            double inst = loan.getInstallmentAmount() == null ? 0 : loan.getInstallmentAmount();
            int periods = inst > 0 ? Math.max(1, (int) Math.floor(amt / inst)) : 1;
            loan.setPaidInstallments(Math.max(0, (loan.getPaidInstallments() == null ? 0 : loan.getPaidInstallments()) - periods));
        }
        if (payment.getDate() != null) loan.setNextDueDate(payment.getDate());
    }

    private java.time.LocalDate nextDate(java.time.LocalDate from, NaveenLoanFrequency frequency, int periods) {
        if (from == null) return null;
        NaveenLoanFrequency f = frequency == null ? NaveenLoanFrequency.Daily : frequency;
        return switch (f) {
            case Weekly -> from.plusWeeks(periods);
            case Monthly -> from.plusMonths(periods);
            default -> from.plusDays(periods); // Daily and Custom both default to a daily cadence
        };
    }

    private void recompute(NaveenLoan loan) {
        double amount = loan.getAmount() == null ? 0 : loan.getAmount();
        double totalPaid = loan.getTotalPaid() == null ? 0 : loan.getTotalPaid();
        loan.setPendingAmount(Math.max(0, amount - totalPaid));
        Integer paidInst = loan.getPaidInstallments() == null ? 0 : loan.getPaidInstallments();
        Integer totalInst = loan.getTotalInstallments();
        if (loan.getPendingAmount() <= 0.01 || (totalInst != null && paidInst >= totalInst)) {
            loan.setStatus(NaveenLoanStatus.Completed);
        } else {
            loan.setStatus(NaveenLoanStatus.Running);
        }
    }

    public double totalOutstandingReceivable() {
        return loanRepository.findAll().stream()
                .mapToDouble(l -> l.getPendingAmount() == null ? 0 : l.getPendingAmount())
                .sum();
    }

    public List<NaveenLoanPayment> paymentsOn(java.time.LocalDate date) {
        return paymentRepository.findByDate(date);
    }
}
