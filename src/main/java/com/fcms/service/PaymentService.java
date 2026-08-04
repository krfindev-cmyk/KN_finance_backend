package com.fcms.service;

import com.fcms.dto.PaymentEditRequest;
import com.fcms.dto.TimelineEntry;
import com.fcms.model.*;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Full installment-by-installment schedule for one loan (e.g. all 100 days for a
     * Rs. 1,00,000 / Rs. 1,000-a-day Daily loan), merged with whatever payments were actually
     * recorded. Days before today with no matching payment show as "Missed"; today with no
     * payment yet shows as "Due"; days after today show as "Pending".
     */
    public List<TimelineEntry> getTimeline(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        List<TimelineEntry> entries = new ArrayList<>();
        if (customer.getStartDate() == null || customer.getTotalInstallments() == null) {
            return entries;
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // Group recorded payments by date so multiple payments on the same day (e.g. a Partial
        // followed later by a top-up) are both surfaced against that installment slot.
        List<Payment> payments = paymentRepository.findByCustomerIdOrderByDateDesc(customerId);
        Map<LocalDate, List<Payment>> byDate = payments.stream()
                .filter(p -> p.getDate() != null)
                .collect(Collectors.groupingBy(Payment::getDate));

        for (int i = 0; i < customer.getTotalInstallments(); i++) {
            LocalDate slotDate = customer.getFinanceType() == FinanceType.Weekly
                    ? customer.getStartDate().plusWeeks(i)
                    : customer.getStartDate().plusDays(i);

            List<Payment> onDate = byDate.get(slotDate);
            boolean isToday = slotDate.isEqual(today);

            if (onDate != null && !onDate.isEmpty()) {
                Payment latest = onDate.stream()
                        .max(Comparator.comparing(Payment::getId))
                        .orElse(onDate.get(0));
                entries.add(new TimelineEntry(i + 1, slotDate, latest.getType().name(), latest.getAmount(), latest.getId(), isToday));
            } else if (slotDate.isAfter(today)) {
                entries.add(new TimelineEntry(i + 1, slotDate, "Pending", null, null, false));
            } else if (isToday) {
                entries.add(new TimelineEntry(i + 1, slotDate, "Due", null, null, true));
            } else {
                entries.add(new TimelineEntry(i + 1, slotDate, "Missed", 0.0, null, false));
            }
        }

        return entries;
    }

    public Payment recordPayment(Payment payment) {
        payment.setCreatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        Customer customer = customerRepository.findById(payment.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        double amt = payment.getAmount() == null ? 0 : payment.getAmount();

        // Drives the "Payment Status" filter on the Customers list (Paid / Partial / NotPaid / Advance),
        // independent of whether the payment counts toward totalPaid.
        customer.setLastPaymentType(payment.getType().name());

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
