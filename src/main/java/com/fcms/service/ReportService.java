package com.fcms.service;

import com.fcms.dto.DailyReport;
import com.fcms.dto.DailyReportRow;
import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.FinanceType;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public ReportService(CustomerRepository customerRepository, PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Everything for the Daily Report: today's org-wide totals (how much was due, how much
     * actually came in, how much is still outstanding for today, and a Paid/NotPaid/
     * Partial/Advance breakdown), plus one row per Running customer showing their loan, their
     * daily installment, how many days they've paid, and whether today is marked yet.
     */
    public DailyReport dailyReport(LocalDate date) {
        LocalDate reportDate = date != null ? date : LocalDate.now(IST);

        List<Payment> todaysPayments = paymentRepository.findByDate(reportDate);
        Map<Long, Payment> paymentByCustomer = new HashMap<>();
        for (Payment p : todaysPayments) {
            paymentByCustomer.put(p.getCustomerId(), p);
        }

        List<Customer> running = customerRepository.findAll().stream()
                .filter(c -> c.getStatus() == CustomerStatus.Running)
                .sorted(Comparator.comparing(Customer::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        double totalToCollect = 0;
        double totalCollected = 0;
        int paidCount = 0, notPaidCount = 0, partialCount = 0, advanceCount = 0;
        List<DailyReportRow> rows = new java.util.ArrayList<>();

        for (Customer c : running) {
            double installment = c.getInstallmentAmount() == null ? 0 : c.getInstallmentAmount();
            Payment todays = paymentByCustomer.get(c.getId());
            // A customer counts toward "today's total due" either because they still show due
            // as of today, OR because they already have a marking for today (which, once Paid,
            // moves nextDueDate forward — so nextDueDate alone would otherwise miss them).
            boolean dueOnReportDate = todays != null
                    || (c.getNextDueDate() != null && !c.getNextDueDate().isAfter(reportDate));

            DailyReportRow row = new DailyReportRow();
            row.setCustomerId(c.getId());
            row.setName(c.getName());
            row.setMobile(c.getMobile());
            row.setTotalLoanAmount(c.getTotalAmount());
            row.setTotalPaid(c.getTotalPaid());
            row.setBalanceAmount(c.getPendingAmount());
            row.setDailyCollection(installment);
            row.setDaysPaid(c.getPaidInstallments());
            row.setTotalInstallments(c.getTotalInstallments());

            if (todays != null) {
                row.setTodayStatus(todays.getType().name());
                row.setTodayAmount(todays.getAmount());
                totalCollected += todays.getType() == PaymentType.NotPaid ? 0 : (todays.getAmount() == null ? 0 : todays.getAmount());
                switch (todays.getType()) {
                    case Paid -> paidCount++;
                    case NotPaid -> notPaidCount++;
                    case Partial -> partialCount++;
                    case Advance -> advanceCount++;
                }
            } else if (dueOnReportDate) {
                row.setTodayStatus("Pending");
            } else {
                row.setTodayStatus("Not Due Yet");
            }

            if (dueOnReportDate) {
                totalToCollect += installment;
            }

            rows.add(row);
        }

        DailyReport report = new DailyReport();
        report.setDate(reportDate);
        report.setTotalToCollect(round2(totalToCollect));
        report.setTotalCollected(round2(totalCollected));
        report.setTotalNotCollected(round2(Math.max(0, totalToCollect - totalCollected)));
        report.setPaidCount(paidCount);
        report.setNotPaidCount(notPaidCount);
        report.setPartialCount(partialCount);
        report.setAdvanceCount(advanceCount);
        report.setRows(rows);
        return report;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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

        DailyReport todayReport = dailyReport(null);

        Map<String, Object> map = new HashMap<>();
        map.put("totalCustomers", all.size());
        map.put("totalFinanced", totalFinanced);
        map.put("totalCollected", totalCollected);
        map.put("totalPending", totalPending);
        map.put("runningCount", running);
        map.put("completedCount", completed);
        map.put("closedCount", closed);
        map.put("overdueCount", overdue);
        // Today's collection snapshot (Indian time), separate from the all-time totals above.
        map.put("todayToCollect", todayReport.getTotalToCollect());
        map.put("todayCollected", todayReport.getTotalCollected());
        map.put("todayNotCollected", todayReport.getTotalNotCollected());
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
