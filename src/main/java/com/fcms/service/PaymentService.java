package com.fcms.service;

import com.fcms.dto.PaymentEditRequest;
import com.fcms.model.*;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository, CustomerRepository customerRepository,
                           CustomerService customerService, AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    public List<Payment> getByCustomer(Long customerId) {
        return paymentRepository.findByCustomerIdOrderByDateDesc(customerId);
    }

    public List<Payment> getAll() {
        return paymentRepository.findAll();
    }

    public Payment recordPayment(Payment payment) {
        payment.setCreatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        Customer customer = customerRepository.findById(payment.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        double amt = payment.getAmount() == null ? 0 : payment.getAmount();

        if (payment.getType() == PaymentType.Paid || payment.getType() == PaymentType.Partial
                || payment.getType() == PaymentType.Advance) {
            customer.setTotalPaid((customer.getTotalPaid() == null ? 0 : customer.getTotalPaid()) + amt);
            customer.setLastPaymentDate(payment.getDate());
            customer.setLastPaymentAmount(amt);

            if (payment.getType() == PaymentType.Paid) {
                // Fully covers this installment: advance one period and count it.
                customer.setPaidInstallments((customer.getPaidInstallments() == null ? 0 : customer.getPaidInstallments()) + 1);
                customer.setNextDueDate(customerService.computeNextDueDate(payment.getDate(), customer.getFinanceType()));
            } else if (payment.getType() == PaymentType.Partial) {
                // Doesn't fully cover the installment: due date stays as-is, still counts toward pending.
                // No change to nextDueDate or paidInstallments.
            } else { // Advance
                Double installmentAmountObj = customer.getInstallmentAmount();
                double installmentAmount = installmentAmountObj == null ? 0 : installmentAmountObj;
                if (installmentAmount <= 0) {
                    // Fallback: no installment amount known, just advance by one period.
                    customer.setNextDueDate(customerService.computeNextDueDate(payment.getDate(), customer.getFinanceType()));
                } else {
                    int periodsCovered = (int) Math.floor(amt / installmentAmount);
                    if (periodsCovered < 1) periodsCovered = 1;
                    customer.setNextDueDate(customerService.computeNextDueDate(payment.getDate(), customer.getFinanceType(), periodsCovered));
                    customer.setPaidInstallments((customer.getPaidInstallments() == null ? 0 : customer.getPaidInstallments()) + periodsCovered);
                    // Any remainder beyond whole installments covered is left as extra credit in totalPaid.
                }
            }
        }
        // NotPaid: add 0 to totalPaid, leave nextDueDate unchanged (still due).

        customerService.recomputeDerived(customer);
        customerRepository.save(customer);

        auditService.log("Payment", saved.getId(), customer.getName(), "payment",
                null, payment.getType() + " - " + payment.getAmount(), payment.getCollectedBy(), "New payment recorded");

        return saved;
    }

    public Payment editPayment(Long paymentId, PaymentEditRequest req) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        Customer customer = customerRepository.findById(payment.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        Double oldAmount = payment.getAmount();
        var oldDate = payment.getDate();
        String oldNotes = payment.getNotes();
        PaymentType oldType = payment.getType();

        // Compute the customer's totalPaid contribution BEFORE and AFTER this edit as a single
        // delta, so that editing amount and type together (or independently) never double-counts
        // or under-counts. A payment only contributes to totalPaid while its type is "counted"
        // (Paid / Partial / Advance) — NotPaid always contributes 0 regardless of its amount field.
        boolean oldCounted = oldType == PaymentType.Paid || oldType == PaymentType.Partial || oldType == PaymentType.Advance;
        double oldContribution = oldCounted ? (oldAmount == null ? 0 : oldAmount) : 0;

        PaymentType newType = req.getType() != null ? req.getType() : oldType;
        double newAmountValue = req.getAmount() != null ? req.getAmount() : (oldAmount == null ? 0 : oldAmount);
        boolean newCounted = newType == PaymentType.Paid || newType == PaymentType.Partial || newType == PaymentType.Advance;
        double newContribution = newCounted ? newAmountValue : 0;

        if (req.getAmount() != null && !req.getAmount().equals(oldAmount)) {
            auditService.log("Payment", payment.getId(), customer.getName(), "amount",
                    oldAmount, req.getAmount(), req.getEditedBy(), req.getReason());
            payment.setAmount(req.getAmount());
        }

        if (req.getDate() != null && !req.getDate().equals(oldDate)) {
            auditService.log("Payment", payment.getId(), customer.getName(), "date",
                    oldDate, req.getDate(), req.getEditedBy(), req.getReason());
            payment.setDate(req.getDate());
        }

        if (req.getNotes() != null && !req.getNotes().equals(oldNotes)) {
            auditService.log("Payment", payment.getId(), customer.getName(), "notes",
                    oldNotes, req.getNotes(), req.getEditedBy(), req.getReason());
            payment.setNotes(req.getNotes());
        }

        if (req.getType() != null && req.getType() != oldType) {
            auditService.log("Payment", payment.getId(), customer.getName(), "type",
                    oldType, req.getType(), req.getEditedBy(), req.getReason());
            payment.setType(req.getType());
            // NOTE: paidInstallments / nextDueDate are intentionally left untouched on edit to avoid
            // corrupting installment schedules already advanced by subsequent payments. This is a
            // known simplification; only the totalPaid ledger is corrected below.
        }

        double delta = newContribution - oldContribution;
        if (delta != 0) {
            customer.setTotalPaid((customer.getTotalPaid() == null ? 0 : customer.getTotalPaid()) + delta);
        }

        payment.setEdited(true);
        payment.setEditedAt(LocalDateTime.now());
        payment.setEditedBy(req.getEditedBy());
        payment.setEditReason(req.getReason());

        customerService.recomputeDerived(customer);
        customerRepository.save(customer);

        return paymentRepository.save(payment);
    }
}
