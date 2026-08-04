package com.fcms.service;

import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.FinanceType;
import com.fcms.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    public List<Customer> getAll() {
        return customerRepository.findAll();
    }

    public Optional<Customer> getById(Long id) {
        return customerRepository.findById(id);
    }

    public Customer create(Customer c) {
        if (c.getGroupKey() == null || c.getGroupKey().isBlank()) {
            c.setGroupKey(slug(c.getName()));
        } else {
            c.setGroupKey(slug(c.getGroupKey()));
        }
        recomputeDerived(c);
        c.setCreatedAt(LocalDate.now().atStartOfDay());
        c.setPaidInstallments(c.getPaidInstallments() == null ? 0 : c.getPaidInstallments());
        c.setTotalPaid(c.getTotalPaid() == null ? 0.0 : c.getTotalPaid());
        recomputeDerived(c);
        if (c.getStatus() == null) c.setStatus(CustomerStatus.Running);
        return customerRepository.save(c);
    }

    /**
     * Normalizes a name/group label into a stable lookup key (lowercase, trimmed,
     * spaces collapsed to single hyphens) so "Ranjith Kumar" and "ranjith  kumar" group together.
     */
    public static String slug(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** All loan accounts (including this one) that belong to the same real-world person. */
    public List<Customer> getLoansForGroup(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) return List.of();
        return customerRepository.findAllByGroupKeyOrderByStartDateAsc(groupKey);
    }

    public Customer update(Long id, Customer updated, String editedBy, String reason) {
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));

        auditField(existing, updated, "name", existing.getName(), updated.getName(), editedBy, reason);
        auditField(existing, updated, "mobile", existing.getMobile(), updated.getMobile(), editedBy, reason);
        auditField(existing, updated, "financeAmount", existing.getFinanceAmount(), updated.getFinanceAmount(), editedBy, reason);
        auditField(existing, updated, "status", existing.getStatus(), updated.getStatus(), editedBy, reason);

        existing.setName(updated.getName());
        existing.setMobile(updated.getMobile());
        existing.setAlternateMobile(updated.getAlternateMobile());
        existing.setAddress(updated.getAddress());
        if (updated.getGroupKey() != null && !updated.getGroupKey().isBlank()) {
            existing.setGroupKey(slug(updated.getGroupKey()));
        } else if (existing.getGroupKey() == null || existing.getGroupKey().isBlank()) {
            existing.setGroupKey(slug(existing.getName()));
        }
        existing.setFinanceAmount(updated.getFinanceAmount());
        existing.setInterest(updated.getInterest());
        existing.setStartDate(updated.getStartDate());
        existing.setFinanceType(updated.getFinanceType());
        existing.setCollectionDay(updated.getCollectionDay());
        existing.setInstallmentAmount(updated.getInstallmentAmount());
        existing.setTotalInstallments(updated.getTotalInstallments());
        if (updated.getStatus() != null) existing.setStatus(updated.getStatus());

        recomputeDerived(existing);
        return customerRepository.save(existing);
    }

    private void auditField(Customer existing, Customer updated, String field, Object oldV, Object newV, String editedBy, String reason) {
        if (newV != null && !newV.equals(oldV)) {
            auditService.log("Customer", existing.getId(), existing.getName(), field, oldV, newV, editedBy, reason);
        }
    }

    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    public void recomputeDerived(Customer c) {
        double financeAmount = c.getFinanceAmount() == null ? 0 : c.getFinanceAmount();
        double interest = c.getInterest() == null ? 0 : c.getInterest();
        double totalAmount = financeAmount + (financeAmount * interest / 100.0);
        c.setTotalAmount(totalAmount);

        double totalPaid = c.getTotalPaid() == null ? 0 : c.getTotalPaid();
        double pending = Math.max(0, totalAmount - totalPaid);
        c.setPendingAmount(pending);
        c.setCurrentBalance(pending);

        if (c.getNextDueDate() == null && c.getStartDate() != null) {
            c.setNextDueDate(computeNextDueDate(c.getStartDate(), c.getFinanceType()));
        }

        if (c.getStartDate() != null && c.getTotalInstallments() != null) {
            c.setEndDate(computeNextDueDate(c.getStartDate(), c.getFinanceType(), c.getTotalInstallments()));
        }

        if (pending <= 0 && c.getStatus() != CustomerStatus.Closed) {
            c.setStatus(CustomerStatus.Completed);
        }
    }

    public LocalDate computeNextDueDate(LocalDate from, FinanceType type) {
        return computeNextDueDate(from, type, 1);
    }

    /**
     * Advances the given date by `periods` installment periods (days for Daily,
     * weeks for Weekly). Used for Advance payments that cover multiple installments.
     */
    public LocalDate computeNextDueDate(LocalDate from, FinanceType type, int periods) {
        if (periods < 1) periods = 1;
        if (type == FinanceType.Weekly) {
            return from.plusWeeks(periods);
        }
        return from.plusDays(periods);
    }

    /**
     * Customers whose next payment is due today or is overdue (nextDueDate < today),
     * restricted to Running status. Used for the "Quick Collection" due-today list.
     *
     * Loans that are overdue (nextDueDate before today) are always shown, since collection
     * of a missed day can happen any time. A loan whose payment is due exactly today only
     * becomes visible on Quick Collection after 12:00 noon, so collectors aren't shown
     * "today's" installment before the day's collection round has actually started.
     */
    public List<Customer> getDueToday() {
        LocalDate today = LocalDate.now();
        boolean todayVisible = !LocalTime.now().isBefore(LocalTime.NOON);
        return customerRepository.findAll().stream()
                .filter(c -> c.getStatus() == CustomerStatus.Running)
                .filter(c -> c.getNextDueDate() != null)
                .filter(c -> {
                    if (c.getNextDueDate().isBefore(today)) return true;
                    if (c.getNextDueDate().isEqual(today)) return todayVisible;
                    return false;
                })
                .toList();
    }

    /**
     * In-memory filtered search over all customers. All provided filters are combined with AND.
     */
    public List<Customer> search(String q, CustomerStatus status, FinanceType financeType,
                                  String paymentStatus, Boolean overdue) {
        LocalDate today = LocalDate.now();
        String qLower = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        return customerRepository.findAll().stream()
                .filter(c -> qLower == null
                        || (c.getName() != null && c.getName().toLowerCase().contains(qLower))
                        || (c.getMobile() != null && c.getMobile().toLowerCase().contains(qLower)))
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> financeType == null || c.getFinanceType() == financeType)
                .filter(c -> {
                    if (paymentStatus == null || paymentStatus.isBlank()) return true;
                    double pending = c.getPendingAmount() == null ? 0 : c.getPendingAmount();
                    if ("Paid".equalsIgnoreCase(paymentStatus)) return pending <= 0;
                    if ("Pending".equalsIgnoreCase(paymentStatus)) return pending > 0;
                    return true;
                })
                .filter(c -> {
                    if (overdue == null) return true;
                    boolean isOverdue = c.getStatus() == CustomerStatus.Running
                            && c.getNextDueDate() != null && c.getNextDueDate().isBefore(today);
                    return overdue == isOverdue;
                })
                .toList();
    }
}
