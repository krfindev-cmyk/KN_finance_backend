package com.fcms.service;

import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.FinanceType;
import com.fcms.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final CustomerRepository customerRepository;

    public ReportService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Map<String, Object> orgSummary() {
        List<Customer> all = customerRepository.findAll();
        double totalFinanced = all.stream().mapToDouble(c -> c.getFinanceAmount() == null ? 0 : c.getFinanceAmount()).sum();
        double totalCollected = all.stream().mapToDouble(c -> c.getTotalPaid() == null ? 0 : c.getTotalPaid()).sum();
        double totalPending = all.stream().mapToDouble(c -> c.getPendingAmount() == null ? 0 : c.getPendingAmount()).sum();
        long running = all.stream().filter(c -> c.getStatus() == CustomerStatus.Running).count();
        long completed = all.stream().filter(c -> c.getStatus() == CustomerStatus.Completed).count();
        long closed = all.stream().filter(c -> c.getStatus() == CustomerStatus.Closed).count();
        long overdue = all.stream().filter(this::isOverdue).count();

        Map<String, Object> map = new HashMap<>();
        map.put("totalCustomers", all.size());
        map.put("totalFinanced", totalFinanced);
        map.put("totalCollected", totalCollected);
        map.put("totalPending", totalPending);
        map.put("runningCount", running);
        map.put("completedCount", completed);
        map.put("closedCount", closed);
        map.put("overdueCount", overdue);
        return map;
    }

    public Map<String, Object> customerReport(Long id) {
        Customer c = customerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Map<String, Object> map = new HashMap<>();
        map.put("customer", c);
        map.put("daysOrWeeksLeft", daysOrWeeksLeft(c));
        map.put("isOverdue", isOverdue(c));
        return map;
    }

    public boolean isOverdue(Customer c) {
        if (c.getNextDueDate() == null || c.getStatus() != CustomerStatus.Running) return false;
        return c.getNextDueDate().isBefore(LocalDate.now());
    }

    public String daysOrWeeksLeft(Customer c) {
        if (c.getNextDueDate() == null) return "N/A";
        long days = ChronoUnit.DAYS.between(LocalDate.now(), c.getNextDueDate());
        if (c.getFinanceType() == FinanceType.Weekly) {
            long weeks = Math.floorDiv(days, 7);
            if (days < 0) return Math.abs(weeks == 0 ? days / 7 : weeks) + " week(s) overdue";
            return weeks + " week(s) left";
        } else {
            if (days < 0) return Math.abs(days) + " day(s) overdue";
            return days + " day(s) left";
        }
    }
}
