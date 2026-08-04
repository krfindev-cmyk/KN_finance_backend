package com.fcms.service;

import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.pdf.PdfTableWriter;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PdfReportService {

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public PdfReportService(CustomerRepository customerRepository, PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }

    private Color colorFor(PaymentType type) {
        if (type == null) return Color.WHITE;
        return switch (type) {
            case Paid -> PdfTableWriter.PAID_COLOR;
            case NotPaid -> PdfTableWriter.PENDING_COLOR;
            case Partial -> PdfTableWriter.PARTIAL_COLOR;
            case Advance -> PdfTableWriter.ADVANCE_COLOR;
        };
    }

    private Color textColorFor(PaymentType type) {
        return Color.WHITE;
    }

    public byte[] customerStatement(Long customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        List<Payment> payments = paymentRepository.findByCustomerIdOrderByDateDesc(customerId).stream()
                .sorted(Comparator.comparing(Payment::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            String title = "Customer Statement - " + safe(c.getName()) + " (" + safe(c.getMobile()) + ")"
                    + " | Finance: " + fmt(c.getFinanceAmount()) + " | Next Due: " + str(c.getNextDueDate());

            PdfTableWriter writer = new PdfTableWriter(document, title,
                    new String[]{"Date", "Type", "Amount", "Collected By", "Notes"},
                    new int[]{15, 15, 15, 20, 35});

            double total = 0;
            for (Payment p : payments) {
                writer.addRow(new String[]{str(p.getDate()), str(p.getType()), fmt(p.getAmount()), safe(p.getCollectedBy()), safe(p.getNotes())},
                        colorFor(p.getType()), textColorFor(p.getType()));
                if (p.getType() != PaymentType.NotPaid) total += p.getAmount() == null ? 0 : p.getAmount();
            }
            writer.addFooter("Total Paid: " + fmt(total) + "   |   Total Amount: " + fmt(c.getTotalAmount())
                    + "   |   Pending: " + fmt(c.getPendingAmount()) + "   |   Next Due Date: " + str(c.getNextDueDate()));
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate customer statement PDF", e);
        }
    }

    public byte[] receipt(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        Customer c = customerRepository.findById(p.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfTableWriter writer = new PdfTableWriter(document, "Payment Receipt #" + p.getId(),
                    new String[]{"Field", "Value"}, new int[]{30, 70});

            writer.addRow(new String[]{"Customer", safe(c.getName())}, PdfTableWriter.WHITE, Color.BLACK);
            writer.addRow(new String[]{"Mobile", safe(c.getMobile())}, PdfTableWriter.WHITE, Color.BLACK);
            writer.addRow(new String[]{"Payment Date", str(p.getDate())}, PdfTableWriter.WHITE, Color.BLACK);
            writer.addRow(new String[]{"Amount", fmt(p.getAmount())}, colorFor(p.getType()), textColorFor(p.getType()));
            writer.addRow(new String[]{"Type", str(p.getType())}, colorFor(p.getType()), textColorFor(p.getType()));
            writer.addRow(new String[]{"Collected By", safe(p.getCollectedBy())}, PdfTableWriter.WHITE, Color.BLACK);
            writer.addRow(new String[]{"Notes", safe(p.getNotes())}, PdfTableWriter.WHITE, Color.BLACK);
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate receipt PDF", e);
        }
    }

    public byte[] dailyCollection(LocalDate date) {
        List<Payment> payments = paymentRepository.findByDate(date);
        return collectionReport("Daily Collection Report - " + date, payments);
    }

    public byte[] weeklyCollection(LocalDate date) {
        LocalDate weekStart = date.with(WeekFields.of(Locale.getDefault()).getFirstDayOfWeek());
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Payment> payments = paymentRepository.findByDateBetween(weekStart, weekEnd);
        return collectionReport("Weekly Collection Report - " + weekStart + " to " + weekEnd, payments);
    }

    public byte[] monthlyCollection(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<Payment> payments = paymentRepository.findByDateBetween(ym.atDay(1), ym.atEndOfMonth());
        return collectionReport("Monthly Collection Report - " + ym, payments);
    }

    private byte[] collectionReport(String title, List<Payment> payments) {
        payments = payments.stream()
                .sorted(Comparator.comparing(Payment::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfTableWriter writer = new PdfTableWriter(document, title,
                    new String[]{"Date", "Customer", "Type", "Amount", "Collected By"},
                    new int[]{15, 30, 15, 15, 25});

            double total = 0;
            for (Payment p : payments) {
                String customerName = customerRepository.findById(p.getCustomerId())
                        .map(Customer::getName).orElse("#" + p.getCustomerId());
                writer.addRow(new String[]{str(p.getDate()), safe(customerName), str(p.getType()), fmt(p.getAmount()), safe(p.getCollectedBy())},
                        colorFor(p.getType()), textColorFor(p.getType()));
                if (p.getType() != PaymentType.NotPaid) total += p.getAmount() == null ? 0 : p.getAmount();
            }
            writer.addFooter("Total Collected: " + fmt(total) + "   |   Payment Count: " + payments.size());
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate collection PDF", e);
        }
    }

    public byte[] pendingReport() {
        List<Customer> customers = customerRepository.findAll().stream()
                .filter(c -> c.getPendingAmount() != null && c.getPendingAmount() > 0)
                .sorted(Comparator.comparing(Customer::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfTableWriter writer = new PdfTableWriter(document, "Pending Customers Report",
                    new String[]{"Name", "Mobile", "Status", "Next Due", "Pending Amount"},
                    new int[]{25, 20, 15, 15, 25});

            double total = 0;
            for (Customer c : customers) {
                boolean overdue = c.getStatus() == CustomerStatus.Running && c.getNextDueDate() != null
                        && c.getNextDueDate().isBefore(LocalDate.now());
                Color bg = overdue ? PdfTableWriter.PENDING_COLOR : PdfTableWriter.WHITE;
                Color text = overdue ? Color.WHITE : Color.BLACK;
                writer.addRow(new String[]{safe(c.getName()), safe(c.getMobile()), str(c.getStatus()), str(c.getNextDueDate()), fmt(c.getPendingAmount())},
                        bg, text);
                total += c.getPendingAmount() == null ? 0 : c.getPendingAmount();
            }
            writer.addFooter("Total Pending: " + fmt(total) + "   |   Customer Count: " + customers.size());
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate pending report PDF", e);
        }
    }

    public byte[] recoveryReport() {
        List<Customer> customers = customerRepository.findAll().stream()
                .sorted(Comparator.comparing(Customer::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfTableWriter writer = new PdfTableWriter(document, "Recovery Status Report",
                    new String[]{"Name", "Finance Amt", "Total Amt", "Total Paid", "Recovery %"},
                    new int[]{25, 18, 18, 18, 21});

            for (Customer c : customers) {
                double totalAmount = c.getTotalAmount() == null ? 0 : c.getTotalAmount();
                double totalPaid = c.getTotalPaid() == null ? 0 : c.getTotalPaid();
                double recoveryPct = totalAmount > 0 ? (totalPaid / totalAmount) * 100.0 : 0.0;
                Color bg = recoveryPct >= 100 ? PdfTableWriter.PAID_COLOR
                        : recoveryPct > 0 ? PdfTableWriter.PARTIAL_COLOR : PdfTableWriter.PENDING_COLOR;
                writer.addRow(new String[]{safe(c.getName()), fmt(c.getFinanceAmount()), fmt(totalAmount), fmt(totalPaid), String.format("%.1f%%", recoveryPct)},
                        bg, Color.WHITE);
            }
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate recovery report PDF", e);
        }
    }

    public byte[] ledger() {
        List<Payment> payments = paymentRepository.findAll().stream()
                .sorted(Comparator.comparing(Payment::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfTableWriter writer = new PdfTableWriter(document, "Full Payment Ledger",
                    new String[]{"Date", "Customer", "Type", "Amount", "Collected By"},
                    new int[]{15, 30, 15, 15, 25});

            double total = 0;
            for (Payment p : payments) {
                String customerName = customerRepository.findById(p.getCustomerId())
                        .map(Customer::getName).orElse("#" + p.getCustomerId());
                writer.addRow(new String[]{str(p.getDate()), safe(customerName), str(p.getType()), fmt(p.getAmount()), safe(p.getCollectedBy())},
                        colorFor(p.getType()), textColorFor(p.getType()));
                if (p.getType() != PaymentType.NotPaid) total += p.getAmount() == null ? 0 : p.getAmount();
            }
            writer.addFooter("Total: " + fmt(total) + "   |   Payment Count: " + payments.size());
            writer.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate ledger PDF", e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private String fmt(Double d) { return d == null ? "0.00" : String.format("%.2f", d); }
}
