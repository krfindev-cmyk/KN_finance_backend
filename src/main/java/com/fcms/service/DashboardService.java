package com.fcms.service;

import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.FinanceType;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    private static final List<PaymentType> COLLECTED_TYPES =
            List.of(PaymentType.Paid, PaymentType.Partial, PaymentType.Advance);

    public DashboardService(CustomerRepository customerRepository, PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }

    public Map<String, Object> getDashboard() {
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("todaysCollection", sumCollected(payments, p -> p.getDate() != null && p.getDate().isEqual(today)));

        LocalDate weekStart = today.minusDays(6);
        result.put("weeklyCollection", sumCollected(payments,
                p -> p.getDate() != null && !p.getDate().isBefore(weekStart) && !p.getDate().isAfter(today)));

        YearMonth currentMonth = YearMonth.from(today);
        result.put("monthlyCollection", sumCollected(payments,
                p -> p.getDate() != null && YearMonth.from(p.getDate()).equals(currentMonth)));

        double pendingAmount = customers.stream()
                .filter(c -> c.getStatus() != CustomerStatus.Closed)
                .mapToDouble(c -> c.getPendingAmount() == null ? 0 : c.getPendingAmount())
                .sum();
        result.put("pendingAmount", pendingAmount);

        double overdueAmount = customers.stream()
                .filter(c -> c.getStatus() == CustomerStatus.Running
                        && c.getNextDueDate() != null && c.getNextDueDate().isBefore(today))
                .mapToDouble(c -> c.getPendingAmount() == null ? 0 : c.getPendingAmount())
                .sum();
        result.put("overdueAmount", overdueAmount);

        result.put("totalCustomers", customers.size());
        result.put("activeCustomers", customers.stream().filter(c -> c.getStatus() == CustomerStatus.Running).count());

        double sumTotalPaid = customers.stream().mapToDouble(c -> c.getTotalPaid() == null ? 0 : c.getTotalPaid()).sum();
        double sumTotalAmount = customers.stream().mapToDouble(c -> c.getTotalAmount() == null ? 0 : c.getTotalAmount()).sum();
        double recoveryPercent = sumTotalAmount > 0 ? (sumTotalPaid / sumTotalAmount) * 100.0 : 0.0;
        result.put("recoveryPercent", round2(recoveryPercent));

        result.put("collectionPercent", round2(computeCollectionPercent(customers, today)));

        result.put("dailyTrend", dailyTrend(payments, today));
        result.put("weeklyTrend", weeklyTrend(payments, today));
        result.put("monthlyTrend", monthlyTrend(payments, today));
        result.put("pendingTrend", pendingTrend(customers, payments, today));
        result.put("paymentStatusBreakdown", paymentStatusBreakdown(payments));

        return result;
    }

    private double sumCollected(List<Payment> payments, java.util.function.Predicate<Payment> filter) {
        return payments.stream()
                .filter(filter)
                .filter(p -> COLLECTED_TYPES.contains(p.getType()))
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    /**
     * Approximate collection-to-date percentage: for each Running/Completed customer, computes how many
     * installment periods should have elapsed since startDate up to today (bounded by totalInstallments),
     * multiplies by installmentAmount (capped at totalAmount) to get the "expected so far" amount, then
     * compares aggregate expected vs aggregate totalPaid across all such customers.
     */
    private double computeCollectionPercent(List<Customer> customers, LocalDate today) {
        double totalExpected = 0;
        double totalPaid = 0;
        for (Customer c : customers) {
            if (c.getStatus() == CustomerStatus.Closed) continue;
            if (c.getStartDate() == null || c.getFinanceType() == null) continue;

            long elapsedPeriods;
            if (c.getFinanceType() == FinanceType.Weekly) {
                elapsedPeriods = ChronoUnit.WEEKS.between(c.getStartDate(), today) + 1;
            } else {
                elapsedPeriods = ChronoUnit.DAYS.between(c.getStartDate(), today) + 1;
            }
            if (elapsedPeriods < 0) elapsedPeriods = 0;
            if (c.getTotalInstallments() != null) {
                elapsedPeriods = Math.min(elapsedPeriods, c.getTotalInstallments());
            }

            double installmentAmount = c.getInstallmentAmount() == null ? 0 : c.getInstallmentAmount();
            double expected = elapsedPeriods * installmentAmount;
            double totalAmount = c.getTotalAmount() == null ? Double.MAX_VALUE : c.getTotalAmount();
            expected = Math.min(expected, totalAmount);

            totalExpected += expected;
            totalPaid += (c.getTotalPaid() == null ? 0 : c.getTotalPaid());
        }
        return totalExpected > 0 ? (totalPaid / totalExpected) * 100.0 : 0.0;
    }

    private List<Map<String, Object>> dailyTrend(List<Payment> payments, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            double amount = sumCollected(payments, p -> p.getDate() != null && p.getDate().isEqual(day));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", day.toString());
            entry.put("amount", amount);
            list.add(entry);
        }
        return list;
    }

    private List<Map<String, Object>> weeklyTrend(List<Payment> payments, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate weekEnd = today.minusDays(7L * i);
            LocalDate weekStart = weekEnd.minusDays(6);
            double amount = sumCollected(payments, p -> p.getDate() != null
                    && !p.getDate().isBefore(weekStart) && !p.getDate().isAfter(weekEnd));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("weekLabel", weekStart + " to " + weekEnd);
            entry.put("amount", amount);
            list.add(entry);
        }
        return list;
    }

    private List<Map<String, Object>> monthlyTrend(List<Payment> payments, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        YearMonth current = YearMonth.from(today);
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            double amount = sumCollected(payments, p -> p.getDate() != null && YearMonth.from(p.getDate()).equals(ym));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("monthLabel", ym.format(fmt));
            entry.put("amount", amount);
            list.add(entry);
        }
        return list;
    }

    /**
     * Approximates the org-wide pending amount as it would have looked on each of the last 14 days,
     * using: pendingAsOfDay = sum over customers (already started by that day) of
     * (totalAmount - totalPaidAsOfThatDay), where totalPaidAsOfThatDay is the sum of that customer's
     * collected-type payments with date <= day.
     */
    private List<Map<String, Object>> pendingTrend(List<Customer> customers, List<Payment> payments, LocalDate today) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            double pendingAsOfDay = 0;
            for (Customer c : customers) {
                if (c.getStartDate() == null || c.getStartDate().isAfter(day)) continue;
                double totalAmount = c.getTotalAmount() == null ? 0 : c.getTotalAmount();
                double paidAsOfDay = payments.stream()
                        .filter(p -> p.getCustomerId() != null && p.getCustomerId().equals(c.getId()))
                        .filter(p -> COLLECTED_TYPES.contains(p.getType()))
                        .filter(p -> p.getDate() != null && !p.getDate().isAfter(day))
                        .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                        .sum();
                pendingAsOfDay += Math.max(0, totalAmount - paidAsOfDay);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", day.toString());
            entry.put("amount", pendingAsOfDay);
            list.add(entry);
        }
        return list;
    }

    private Map<String, Object> paymentStatusBreakdown(List<Payment> payments) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paidCount", countByType(payments, PaymentType.Paid));
        map.put("paidAmount", amountByType(payments, PaymentType.Paid));
        map.put("partialCount", countByType(payments, PaymentType.Partial));
        map.put("partialAmount", amountByType(payments, PaymentType.Partial));
        map.put("notPaidCount", countByType(payments, PaymentType.NotPaid));
        map.put("notPaidAmount", amountByType(payments, PaymentType.NotPaid));
        map.put("advanceCount", countByType(payments, PaymentType.Advance));
        map.put("advanceAmount", amountByType(payments, PaymentType.Advance));
        return map;
    }

    private long countByType(List<Payment> payments, PaymentType type) {
        return payments.stream().filter(p -> p.getType() == type).count();
    }

    private double amountByType(List<Payment> payments, PaymentType type) {
        return payments.stream().filter(p -> p.getType() == type)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
