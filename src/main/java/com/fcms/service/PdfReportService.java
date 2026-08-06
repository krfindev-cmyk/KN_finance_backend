package com.fcms.service;

import com.fcms.dto.DailyReport;
import com.fcms.dto.DailyReportRow;
import com.fcms.model.Customer;
import com.fcms.model.CustomerStatus;
import com.fcms.model.Payment;
import com.fcms.model.PaymentType;
import com.fcms.pdf.PdfTableWriter;
import com.fcms.repository.CustomerRepository;
import com.fcms.repository.PaymentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
    private final ReportService reportService;

    public PdfReportService(CustomerRepository customerRepository, PaymentRepository paymentRepository, ReportService reportService) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.reportService = reportService;
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

    /**
     * The main "hand this to the collector" daily report: KR Finance header, today's date,
     * an at-a-glance summary (how much was due, how much came in, how much is still short,
     * and a Paid/Not Paid/Partial/Advance breakdown), then one row per running customer with
     * their loan total, cumulative paid, remaining balance, daily installment, how many days
     * they've paid so far, and whether today is marked yet.
     */
    public byte[] dailyCollection(LocalDate date) {
        DailyReport report = reportService.dailyReport(date);

        float margin = 40f;
        float rowHeight = 18f;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        int[] colUnits = {22, 14, 14, 14, 14, 10, 12};
        String[] headers = {"Name", "Loan Amt", "Total Paid", "Balance", "Daily Amt", "Days Paid", "Today"};
        int totalUnits = 0;
        for (int u : colUnits) totalUnits += u;
        float usableWidth = pageWidth - 2 * margin;
        float[] colWidths = new float[colUnits.length];
        for (int i = 0; i < colUnits.length; i++) colWidths[i] = usableWidth * colUnits[i] / (float) totalUnits;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float cursorY = pageHeight - margin;

            // ---- Brand header banner ----
            stream.setNonStrokingColor(PdfTableWriter.BRAND_COLOR);
            stream.addRect(margin - 10, cursorY - 32, usableWidth + 20, 58);
            stream.fill();
            cursorY = drawLine(stream, margin, cursorY, "KR Finance", PDType1Font.HELVETICA_BOLD, 20, Color.WHITE, 22);
            cursorY = drawLine(stream, margin, cursorY, "Daily Collection Report  |  " + report.getDate(), PDType1Font.HELVETICA, 11, new Color(219, 234, 254), 24);
            cursorY -= 22;

            // ---- Summary "stat cards" — mirrors the dashboard's own summary tiles ----
            float cardGap = 10f;
            float cardWidth = (usableWidth - 2 * cardGap) / 3f;
            float cardHeight = 46f;
            cursorY = drawStatCards(stream, margin, cursorY, cardWidth, cardHeight, cardGap,
                    new String[]{"To Collect Today", "Collected Today", "Not Collected"},
                    new String[]{fmt(report.getTotalToCollect()), fmt(report.getTotalCollected()), fmt(report.getTotalNotCollected())},
                    new Color[]{PdfTableWriter.ADVANCE_COLOR, PdfTableWriter.PAID_COLOR, PdfTableWriter.PENDING_COLOR});
            cursorY -= 14;

            // ---- Status breakdown pills ----
            cursorY = drawPillRow(stream, margin, cursorY,
                    new String[]{"Paid " + report.getPaidCount(), "Not Paid " + report.getNotPaidCount(),
                            "Partial " + report.getPartialCount(), "Advance " + report.getAdvanceCount()},
                    new Color[]{PdfTableWriter.PAID_COLOR, PdfTableWriter.PENDING_COLOR, PdfTableWriter.PARTIAL_COLOR, PdfTableWriter.ADVANCE_COLOR});
            cursorY -= 12;

            // ---- Table header ----
            cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0);

            int rowIdx = 0;
            for (DailyReportRow row : report.getRows()) {
                if (cursorY - rowHeight < margin) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    cursorY = pageHeight - margin;
                    cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0);
                    rowIdx = 0;
                }
                Color statusColor = statusColor(row.getTodayStatus());
                boolean isStatus = statusColor != PdfTableWriter.WHITE;
                String[] values = {
                        safe(row.getName()),
                        fmt(row.getTotalLoanAmount()),
                        fmt(row.getTotalPaid()),
                        fmt(row.getBalanceAmount()),
                        fmt(row.getDailyCollection()),
                        (row.getDaysPaid() == null ? "0" : row.getDaysPaid()) + "/" + (row.getTotalInstallments() == null ? "-" : row.getTotalInstallments()),
                        safe(row.getTodayStatus())
                };
                cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, values, PDType1Font.HELVETICA, statusColor, isStatus ? statusColor : Color.BLACK, isStatus, rowIdx);
                rowIdx++;
            }

            cursorY -= 8;
            if (cursorY - 16 < margin) {
                stream.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                cursorY = pageHeight - margin;
            }
            drawLine(stream, margin, cursorY, "Generated by KR Finance", PDType1Font.HELVETICA_OBLIQUE, 8, Color.GRAY, 12);
            stream.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate daily collection PDF", e);
        }
    }

    /** Three dashboard-style summary tiles side by side: label on top, big bold value, colored accent bar. */
    private float drawStatCards(PDPageContentStream stream, float margin, float cursorY, float cardWidth, float cardHeight,
                                 float gap, String[] labels, String[] values, Color[] accents) throws IOException {
        float x = margin;
        for (int i = 0; i < labels.length; i++) {
            stream.setNonStrokingColor(new Color(248, 250, 252));
            stream.addRect(x, cursorY - cardHeight, cardWidth, cardHeight);
            stream.fill();
            stream.setNonStrokingColor(accents[i]);
            stream.addRect(x, cursorY - cardHeight, cardWidth, 3);
            stream.fill();
            drawLine(stream, x + 8, cursorY - 15, labels[i], PDType1Font.HELVETICA, 8, Color.GRAY, 0);
            drawLine(stream, x + 8, cursorY - 34, "Rs. " + values[i], PDType1Font.HELVETICA_BOLD, 13, Color.BLACK, 0);
            x += cardWidth + gap;
        }
        return cursorY - cardHeight;
    }

    /** Small rounded-looking status-count badges (Paid / Not Paid / Partial / Advance). */
    private float drawPillRow(PDPageContentStream stream, float margin, float cursorY, String[] labels, Color[] colors) throws IOException {
        float x = margin;
        float pillHeight = 18f;
        for (int i = 0; i < labels.length; i++) {
            float pillWidth = 14 + labels[i].length() * 5.2f;
            stream.setNonStrokingColor(tint(colors[i], 0.85f));
            stream.addRect(x, cursorY - pillHeight, pillWidth, pillHeight);
            stream.fill();
            stream.setNonStrokingColor(colors[i]);
            stream.addRect(x, cursorY - pillHeight, 3, pillHeight);
            stream.fill();
            drawLine(stream, x + 8, cursorY - 13, labels[i], PDType1Font.HELVETICA_BOLD, 9, colors[i], 0);
            x += pillWidth + 8;
        }
        return cursorY - pillHeight;
    }

    private Color tint(Color c, float towardsWhite) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * towardsWhite);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * towardsWhite);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * towardsWhite);
        return new Color(r, g, b);
    }

    private Color statusColor(String status) {
        if (status == null) return PdfTableWriter.WHITE;
        return switch (status) {
            case "Paid" -> PdfTableWriter.PAID_COLOR;
            case "NotPaid" -> PdfTableWriter.PENDING_COLOR;
            case "Partial" -> PdfTableWriter.PARTIAL_COLOR;
            case "Advance" -> PdfTableWriter.ADVANCE_COLOR;
            default -> PdfTableWriter.WHITE; // Pending / Not Due Yet
        };
    }

    private float drawLine(PDPageContentStream stream, float x, float y, String text, org.apache.pdfbox.pdmodel.font.PDFont font,
                            float size, Color color, float advance) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text));
        stream.endText();
        return y - advance;
    }

    /**
     * isStatus rows (Paid/NotPaid/Partial/Advance) get a soft tinted background + colored left
     * accent bar + colored text, like a dashboard status badge. Non-status rows get plain
     * white/zebra-striped backgrounds so the long customer list stays easy to scan.
     */
    private float drawTableRow(PDPageContentStream stream, float margin, float cursorY, float rowHeight, float[] colWidths,
                                String[] values, org.apache.pdfbox.pdmodel.font.PDFont font, Color bg, Color textColor,
                                boolean isStatus, int rowIndex) throws IOException {
        float x = margin;
        float totalWidth = 0;
        for (float w : colWidths) totalWidth += w;

        boolean isHeader = bg != null && bg.equals(PdfTableWriter.HEADER_COLOR);
        Color rowBg;
        if (isHeader) {
            rowBg = bg;
        } else if (isStatus) {
            rowBg = tint(bg, 0.88f);
        } else {
            rowBg = (rowIndex % 2 == 1) ? new Color(248, 250, 252) : Color.WHITE;
        }
        stream.setNonStrokingColor(rowBg);
        stream.addRect(margin, cursorY - rowHeight + 4, totalWidth, rowHeight - 2);
        stream.fill();

        if (isStatus) {
            stream.setNonStrokingColor(bg);
            stream.addRect(margin, cursorY - rowHeight + 4, 3, rowHeight - 2);
            stream.fill();
        }

        for (int i = 0; i < values.length && i < colWidths.length; i++) {
            String v = values[i] == null ? "" : values[i];
            int maxChars = Math.max(3, (int) (colWidths[i] / 5.0));
            if (v.length() > maxChars) v = v.substring(0, maxChars - 1) + "...";
            stream.beginText();
            stream.setFont(font, 9);
            stream.setNonStrokingColor(textColor);
            stream.newLineAtOffset(x + 4, cursorY - rowHeight + 8);
            stream.showText(sanitize(v));
            stream.endText();
            x += colWidths[i];
        }
        return cursorY - rowHeight;
    }

    private String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c < 256 ? c : '?');
        return sb.toString();
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
