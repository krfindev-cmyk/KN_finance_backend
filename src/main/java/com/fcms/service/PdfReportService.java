package com.fcms.service;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.dto.DailyReport;
import com.fcms.dto.DailyReportRow;
import com.fcms.model.CashExpense;
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

    private static final Color TOTALS_ROW_COLOR = new Color(226, 232, 240);

    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ReportService reportService;
    private final CashLedgerService cashLedgerService;

    public PdfReportService(CustomerRepository customerRepository, PaymentRepository paymentRepository,
                             ReportService reportService, CashLedgerService cashLedgerService) {
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.reportService = reportService;
        this.cashLedgerService = cashLedgerService;
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

    /** One payment tagged with which of the person's (possibly several) loans it belongs to. */
    private static final class LoanPayment {
        final int loanNo;
        final Payment payment;
        LoanPayment(int loanNo, Payment payment) { this.loanNo = loanNo; this.payment = payment; }
    }

    /** A little mutable "where are we on the page" bundle so page-break helpers can update it in place. */
    private static final class PdfCursor {
        PDPage page;
        PDPageContentStream stream;
        float y;
        PdfCursor(PDPage page, PDPageContentStream stream, float y) { this.page = page; this.stream = stream; this.y = y; }
    }

    private PdfCursor newCursorPage(PDDocument document, float pageHeight, float margin) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return new PdfCursor(page, new PDPageContentStream(document, page), pageHeight - margin);
    }

    private void ensureRoom(PdfCursor cur, PDDocument document, float pageHeight, float margin, float needed) throws IOException {
        if (cur.y - needed < margin) {
            cur.stream.close();
            PdfCursor fresh = newCursorPage(document, pageHeight, margin);
            cur.page = fresh.page;
            cur.stream = fresh.stream;
            cur.y = fresh.y;
        }
    }

    /**
     * Customer Report PDF: full statement covering every one of this person's loans (grouped by
     * groupKey) — customer details, an at-a-glance overall summary, a loan-wise summary table,
     * each loan's own payment history broken out separately, a combined transaction history
     * across all loans, and a final balance recap at the end.
     */
    public byte[] customerStatement(Long customerId) {
        Customer anchor = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        List<Customer> loans = (anchor.getGroupKey() != null && !anchor.getGroupKey().isBlank())
                ? customerRepository.findAllByGroupKeyOrderByStartDateAsc(anchor.getGroupKey())
                : List.of(anchor);
        if (loans.isEmpty()) loans = List.of(anchor);

        LocalDate today = LocalDate.now();
        double totalFinanced = 0, totalPaidAll = 0, totalPendingAll = 0, totalOverdue = 0;
        int activeLoans = 0, completedLoans = 0;
        LocalDate nextDue = null;
        Double nextDueAmount = null;
        for (Customer c : loans) {
            totalFinanced += c.getFinanceAmount() == null ? 0 : c.getFinanceAmount();
            totalPaidAll += c.getTotalPaid() == null ? 0 : c.getTotalPaid();
            totalPendingAll += c.getPendingAmount() == null ? 0 : c.getPendingAmount();
            boolean overdue = c.getStatus() == CustomerStatus.Running && c.getNextDueDate() != null && c.getNextDueDate().isBefore(today);
            if (overdue) totalOverdue += c.getPendingAmount() == null ? 0 : c.getPendingAmount();
            if (c.getStatus() == CustomerStatus.Running) {
                activeLoans++;
                if (c.getNextDueDate() != null && (nextDue == null || c.getNextDueDate().isBefore(nextDue))) {
                    nextDue = c.getNextDueDate();
                    nextDueAmount = c.getInstallmentAmount();
                }
            } else {
                completedLoans++;
            }
        }

        // Payments per loan (oldest first) for the per-loan history sections, plus one combined
        // list (across every loan, tagged with which loan) for the transaction history section.
        List<List<Payment>> paymentsByLoan = new java.util.ArrayList<>();
        List<LoanPayment> allHistory = new java.util.ArrayList<>();
        for (int i = 0; i < loans.size(); i++) {
            List<Payment> payments = paymentRepository.findByCustomerIdOrderByDateDesc(loans.get(i).getId()).stream()
                    .sorted(Comparator.comparing(Payment::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            paymentsByLoan.add(payments);
            for (Payment p : payments) allHistory.add(new LoanPayment(i + 1, p));
        }
        allHistory.sort(Comparator.comparing((LoanPayment lp) -> lp.payment.getDate(), Comparator.nullsLast(Comparator.naturalOrder())));

        float margin = 40f;
        float rowHeight = 18f;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        float usableWidth = pageWidth - 2 * margin;

        float[] kvColWidths = scaledWidths(new int[]{45, 55}, usableWidth);
        boolean[] kvRightAlign = {false, false};
        boolean[] kvAmountRightAlign = {false, true};

        int[] loanColUnits = {8, 15, 15, 12, 13, 13, 12, 12};
        String[] loanHeaders = {"Loan", "Start Date", "Loan Amt", "Daily/EMI", "Paid", "Balance", "Days Paid", "Status"};
        boolean[] loanRightAlign = {false, false, true, true, true, true, false, false};
        float[] loanColWidths = scaledWidths(loanColUnits, usableWidth);

        int[] loanHistUnits = {8, 18, 18, 18, 18, 20};
        String[] loanHistHeaders = {"S.No", "Date", "Due Amt", "Paid Amt", "Status", "Collected By"};
        boolean[] loanHistRightAlign = {true, false, true, true, false, false};
        float[] loanHistColWidths = scaledWidths(loanHistUnits, usableWidth);

        int[] txnUnits = {8, 17, 12, 15, 17, 31};
        String[] txnHeaders = {"S.No", "Date", "Loan", "Type", "Amount", "Collected By"};
        boolean[] txnRightAlign = {true, false, false, false, true, false};
        float[] txnColWidths = scaledWidths(txnUnits, usableWidth);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfCursor cur = newCursorPage(document, pageHeight, margin);

            // ---- Brand header banner ----
            cur.stream.setNonStrokingColor(PdfTableWriter.BRAND_COLOR);
            cur.stream.addRect(margin - 10, cur.y - 32, usableWidth + 20, 58);
            cur.stream.fill();
            cur.y = drawLine(cur.stream, margin, cur.y, "KR Finance", PDType1Font.HELVETICA_BOLD, 20, Color.WHITE, 22);
            cur.y = drawLine(cur.stream, margin, cur.y, "Customer Report" + (loans.size() > 1 ? "  |  " + loans.size() + " loans" : ""),
                    PDType1Font.HELVETICA, 11, new Color(219, 234, 254), 24);
            cur.y -= 22;

            // ---- 1. Customer Details ----
            cur.y = drawLine(cur.stream, margin, cur.y, "Customer Details", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            String customerIdLabel = anchor.getGroupKey() != null && !anchor.getGroupKey().isBlank() ? anchor.getGroupKey() : String.valueOf(anchor.getId());
            String[][] details = {
                    {"Customer Name", safe(anchor.getName())},
                    {"Customer ID", customerIdLabel},
                    {"Mobile Number", safe(anchor.getMobile())},
                    {"Address", safe(anchor.getAddress())},
                    {"Report Generated Date", str(today)}
            };
            for (String[] row : details) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, row, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, 0, kvRightAlign);
            }
            cur.y -= 12;

            // ---- 2. Overall Summary ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 8);
            cur.y = drawLine(cur.stream, margin, cur.y, "Overall Summary", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"Summary", "Amount"}, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, kvRightAlign);
            String[][] overall = {
                    {"Total Loans", String.valueOf(loans.size())},
                    {"Total Amount Financed", fmt(totalFinanced)},
                    {"Total Amount Paid", fmt(totalPaidAll)},
                    {"Total Balance", fmt(totalPendingAll)},
                    {"Total Overdue", fmt(totalOverdue)},
                    {"Active Loans", String.valueOf(activeLoans)},
                    {"Completed Loans", String.valueOf(completedLoans)}
            };
            for (int i = 0; i < overall.length; i++) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, overall[i], PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, i, kvAmountRightAlign);
            }
            cur.y -= 14;

            // ---- 3. Loan-wise Summary ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 3);
            cur.y = drawLine(cur.stream, margin, cur.y, "Loan-wise Summary", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, loanHeaders, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, loanRightAlign);
            double sumLoanAmt = 0, sumLoanPaid = 0, sumLoanBalance = 0;
            for (int i = 0; i < loans.size(); i++) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                Customer c = loans.get(i);
                String status = c.getStatus() == null ? "-" : c.getStatus().name();
                String[] values = {
                        "Loan #" + String.format("%03d", i + 1),
                        str(c.getStartDate()),
                        fmt(c.getTotalAmount()),
                        fmt(c.getInstallmentAmount()),
                        fmt(c.getTotalPaid()),
                        fmt(c.getPendingAmount()),
                        (c.getPaidInstallments() == null ? "0" : c.getPaidInstallments()) + "/" + (c.getTotalInstallments() == null ? "-" : c.getTotalInstallments()),
                        status
                };
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, values, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, i, loanRightAlign);
                sumLoanAmt += c.getTotalAmount() == null ? 0 : c.getTotalAmount();
                sumLoanPaid += c.getTotalPaid() == null ? 0 : c.getTotalPaid();
                sumLoanBalance += c.getPendingAmount() == null ? 0 : c.getPendingAmount();
            }
            ensureRoom(cur, document, pageHeight, margin, rowHeight);
            String[] loanTotals = {"TOTAL", "", fmt(sumLoanAmt), "", fmt(sumLoanPaid), fmt(sumLoanBalance), "", ""};
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, loanTotals, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, loanRightAlign);

            // ---- 4. Each loan's own payment history, starting fresh from the next page ----
            cur.stream.close();
            cur = newCursorPage(document, pageHeight, margin);
            for (int i = 0; i < loans.size(); i++) {
                Customer c = loans.get(i);
                List<Payment> payments = paymentsByLoan.get(i);
                ensureRoom(cur, document, pageHeight, margin, rowHeight * 2);
                cur.y = drawLine(cur.stream, margin, cur.y, "Loan #" + String.format("%03d", i + 1) + "  —  " + fmt(c.getTotalAmount()), PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanHistColWidths, loanHistHeaders, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, loanHistRightAlign);
                int rIdx = 0;
                for (Payment p : payments) {
                    ensureRoom(cur, document, pageHeight, margin, rowHeight);
                    Color statusColor = colorFor(p.getType());
                    boolean isStatus = statusColor != PdfTableWriter.WHITE;
                    String[] values = {
                            String.valueOf(rIdx + 1),
                            str(p.getDate()),
                            fmt(c.getInstallmentAmount()),
                            fmt(p.getAmount()),
                            str(p.getType()),
                            safe(p.getCollectedBy())
                    };
                    cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanHistColWidths, values, PDType1Font.HELVETICA, statusColor, isStatus ? statusColor : Color.BLACK, isStatus, rIdx, loanHistRightAlign);
                    rIdx++;
                }
                if (payments.isEmpty()) {
                    cur.y -= 4;
                    cur.y = drawLine(cur.stream, margin, cur.y, "No payments recorded for this loan yet.", PDType1Font.HELVETICA_OBLIQUE, 9, Color.GRAY, 16);
                }
                cur.y -= 16;
            }

            // ---- 5. Combined Payment Transaction History across every loan ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 3);
            cur.y = drawLine(cur.stream, margin, cur.y, "Payment Transaction History", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, txnColWidths, txnHeaders, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, txnRightAlign);
            int sno = 1, rowIdx = 0;
            for (LoanPayment lp : allHistory) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                Payment p = lp.payment;
                Color statusColor = colorFor(p.getType());
                boolean isStatus = statusColor != PdfTableWriter.WHITE;
                String[] values = {
                        String.valueOf(sno++),
                        str(p.getDate()),
                        loans.size() > 1 ? ("#" + String.format("%03d", lp.loanNo)) : "-",
                        str(p.getType()),
                        fmt(p.getAmount()),
                        safe(p.getCollectedBy())
                };
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, txnColWidths, values, PDType1Font.HELVETICA, statusColor, isStatus ? statusColor : Color.BLACK, isStatus, rowIdx, txnRightAlign);
                rowIdx++;
            }
            if (allHistory.isEmpty()) {
                cur.y -= 4;
                cur.y = drawLine(cur.stream, margin, cur.y, "No payments recorded yet.", PDType1Font.HELVETICA_OBLIQUE, 9, Color.GRAY, 16);
            }
            cur.y -= 14;

            // ---- 6. Final Customer Balance ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 6);
            cur.y = drawLine(cur.stream, margin, cur.y, "Final Customer Balance", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawLine(cur.stream, margin, cur.y, "Total Amount Financed: " + fmt(totalFinanced), PDType1Font.HELVETICA_BOLD, 10, Color.BLACK, 16);
            cur.y = drawLine(cur.stream, margin, cur.y, "Total Amount Collected: " + fmt(totalPaidAll), PDType1Font.HELVETICA_BOLD, 10, PdfTableWriter.PAID_COLOR, 16);
            cur.y = drawLine(cur.stream, margin, cur.y, "Total Outstanding: " + fmt(totalPendingAll), PDType1Font.HELVETICA_BOLD, 10, PdfTableWriter.PENDING_COLOR, 16);
            cur.y = drawLine(cur.stream, margin, cur.y, "Overdue Amount: " + fmt(totalOverdue), PDType1Font.HELVETICA_BOLD, 10, PdfTableWriter.PARTIAL_COLOR, 16);
            cur.y = drawLine(cur.stream, margin, cur.y,
                    "Next Payment Due: " + (nextDue != null ? str(nextDue) + " — " + fmt(nextDueAmount) : "N/A (no active loan)"),
                    PDType1Font.HELVETICA_BOLD, 10, Color.BLACK, 16);

            cur.y -= 10;
            ensureRoom(cur, document, pageHeight, margin, 16);
            drawLine(cur.stream, margin, cur.y, "Generated by KR Finance", PDType1Font.HELVETICA_OBLIQUE, 8, Color.GRAY, 12);
            cur.stream.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate customer statement PDF", e);
        }
    }

    private float[] scaledWidths(int[] units, float usableWidth) {
        int total = 0;
        for (int u : units) total += u;
        float[] widths = new float[units.length];
        for (int i = 0; i < units.length; i++) widths[i] = usableWidth * units[i] / (float) total;
        return widths;
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
        int[] colUnits = {6, 18, 12, 9, 12, 12, 12, 11, 10};
        String[] headers = {"S.No", "Name", "Daily Amt", "Days Paid", "Loan Amt", "Total Paid", "Balance", "Paid Today", "Today"};
        // Header cells right-align in the same columns as their data below, so the column
        // heading sits flush above the numbers instead of drifting to the left of them.
        boolean[] rightAlign = {true, false, true, false, true, true, true, true, false};
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
            cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, rightAlign);

            int rowIdx = 0; // resets each page — drives zebra striping only, which should restart per page
            int sno = 1; // never resets — this is the S.No the user actually reads, continuous across pages
            double sumDaily = 0, sumLoan = 0, sumPaid = 0, sumBalance = 0, sumPaidToday = 0;
            for (DailyReportRow row : report.getRows()) {
                if (cursorY - rowHeight < margin) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    cursorY = pageHeight - margin;
                    cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, rightAlign);
                    rowIdx = 0;
                }
                Color statusColor = statusColor(row.getTodayStatus());
                boolean isStatus = statusColor != PdfTableWriter.WHITE;
                String[] values = {
                        String.valueOf(sno++),
                        safe(row.getName()),
                        fmt(row.getDailyCollection()),
                        (row.getDaysPaid() == null ? "0" : row.getDaysPaid()) + "/" + (row.getTotalInstallments() == null ? "-" : row.getTotalInstallments()),
                        fmt(row.getTotalLoanAmount()),
                        fmt(row.getTotalPaid()),
                        fmt(row.getBalanceAmount()),
                        row.getTodayAmount() == null ? "-" : fmt(row.getTodayAmount()),
                        safe(row.getTodayStatus())
                };
                cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, values, PDType1Font.HELVETICA, statusColor, isStatus ? statusColor : Color.BLACK, isStatus, rowIdx, rightAlign);
                rowIdx++;

                sumDaily += row.getDailyCollection() == null ? 0 : row.getDailyCollection();
                sumLoan += row.getTotalLoanAmount() == null ? 0 : row.getTotalLoanAmount();
                sumPaid += row.getTotalPaid() == null ? 0 : row.getTotalPaid();
                sumBalance += row.getBalanceAmount() == null ? 0 : row.getBalanceAmount();
                sumPaidToday += row.getTodayAmount() == null ? 0 : row.getTodayAmount();
            }

            // ---- Totals row — sums every numeric column except Days Paid, plus today's
            // Paid/Not Paid counts in the last column, so the collector sees a grand total
            // without adding anything up by hand. ----
            if (cursorY - rowHeight < margin) {
                stream.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                cursorY = pageHeight - margin;
                cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, headers, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, rightAlign);
            }
            String[] totalsValues = {
                    "",
                    "TOTAL",
                    fmt(sumDaily),
                    "-",
                    fmt(sumLoan),
                    fmt(sumPaid),
                    fmt(sumBalance),
                    fmt(sumPaidToday),
                    "P:" + report.getPaidCount() + " N:" + report.getNotPaidCount()
            };
            cursorY = drawTableRow(stream, margin, cursorY, rowHeight, colWidths, totalsValues, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, rightAlign);

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

    /**
     * Cash Ledger PDF: today's collection, what was spent/sent, the balance left after
     * spending, and the running total balance carried forward from previous days — plus a
     * line-by-line list of every expense entry logged for the day.
     */
    public byte[] cashLedger(LocalDate date) {
        CashLedgerSummary summary = cashLedgerService.summary(date);
        LocalDate d = summary.getDate();

        // Loan collections for the day: payments joined back to their customer for a name.
        List<Payment> collections = paymentRepository.findByDate(d).stream()
                .filter(p -> p.getType() != PaymentType.NotPaid)
                .sorted(Comparator.comparing(Payment::getId))
                .toList();
        java.util.Map<Long, Customer> customerById = new java.util.HashMap<>();
        for (Customer c : customerRepository.findAllById(collections.stream().map(Payment::getCustomerId).filter(java.util.Objects::nonNull).toList())) {
            customerById.put(c.getId(), c);
        }
        double loanCollections = collections.stream().mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
        double otherIncome = 0;
        double totalInflow = loanCollections + otherIncome;
        double totalOutflow = summary.getExpensesToday();

        // Outflow grouped by category, in a stable display order.
        java.util.LinkedHashMap<String, Double> outflowByCategory = new java.util.LinkedHashMap<>();
        for (CashExpense e : summary.getExpenses()) {
            String key = e.getCategory() == null ? "Other" : e.getCategory().name();
            outflowByCategory.merge(key, e.getAmount() == null ? 0 : e.getAmount(), Double::sum);
        }

        boolean shortage = summary.getClosingBalance() < 0;

        float margin = 40f;
        float rowHeight = 18f;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        float usableWidth = pageWidth - 2 * margin;

        float[] kvColWidths = scaledWidths(new int[]{55, 45}, usableWidth);
        boolean[] kvRightAlign = {false, true};

        int[] txnUnits = {6, 14, 10, 20, 18, 12, 20};
        String[] txnHeaders = {"S.No", "Date", "Type", "Description", "Person", "Mode", "Amount"};
        boolean[] txnRightAlign = {true, false, false, false, false, false, true};
        float[] txnColWidths = scaledWidths(txnUnits, usableWidth);

        int[] loanColUnits = {6, 26, 14, 16, 14, 24};
        String[] loanHeaders = {"S.No", "Customer", "Loan ID", "Amount", "Mode", "Collected By"};
        boolean[] loanRightAlign = {true, false, true, true, false, false};
        float[] loanColWidths = scaledWidths(loanColUnits, usableWidth);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {

            PdfCursor cur = newCursorPage(document, pageHeight, margin);

            // ---- Header ----
            cur.stream.setNonStrokingColor(PdfTableWriter.BRAND_COLOR);
            cur.stream.addRect(margin - 10, cur.y - 32, usableWidth + 20, 58);
            cur.stream.fill();
            cur.y = drawLine(cur.stream, margin, cur.y, "KR FINANCE", PDType1Font.HELVETICA_BOLD, 20, Color.WHITE, 22);
            cur.y = drawLine(cur.stream, margin, cur.y, "CASH LEDGER STATEMENT", PDType1Font.HELVETICA, 11, new Color(219, 234, 254), 24);
            cur.y -= 8;
            cur.y = drawLine(cur.stream, margin, cur.y, "Date: " + str(d) + "        Generated: " + str(LocalDate.now()),
                    PDType1Font.HELVETICA_OBLIQUE, 9, Color.GRAY, 18);
            cur.y -= 10;

            // ---- CASH SUMMARY ----
            cur.y = drawLine(cur.stream, margin, cur.y, "CASH SUMMARY", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            String[][] cashSummary = {
                    {"Opening Balance", fmt(summary.getOpeningBalance())},
                    {"Collections Received", fmt(summary.getCollectedToday())},
                    {"Total Cash Available", fmt(summary.getOpeningBalance() + summary.getCollectedToday())},
                    {"Cash Spent / Sent", fmt(summary.getExpensesToday())}
            };
            for (String[] row : cashSummary) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, row, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, 0, kvRightAlign);
            }
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 2);
            cur.y = drawLine(cur.stream, margin, cur.y, "----------------------------------------", PDType1Font.HELVETICA, 9, Color.LIGHT_GRAY, 12);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths,
                    new String[]{"CLOSING BALANCE", fmt(summary.getClosingBalance())},
                    PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, shortage ? PdfTableWriter.PENDING_COLOR : PdfTableWriter.PAID_COLOR, false, 0, kvRightAlign);
            if (shortage) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                cur.y = drawLine(cur.stream, margin, cur.y, "CASH SHORTAGE: " + fmt(Math.abs(summary.getClosingBalance())),
                        PDType1Font.HELVETICA_BOLD, 10, PdfTableWriter.PENDING_COLOR, 16);
            }
            cur.y -= 12;

            // ---- CASH INFLOW ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 4);
            cur.y = drawLine(cur.stream, margin, cur.y, "CASH INFLOW", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"Loan Collections", fmt(loanCollections)}, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, 0, kvRightAlign);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"Other Income", fmt(otherIncome)}, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, 1, kvRightAlign);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"TOTAL INFLOW", fmt(totalInflow)}, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, kvRightAlign);
            cur.y -= 12;

            // ---- CASH OUTFLOW (by category) ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * (outflowByCategory.size() + 3));
            cur.y = drawLine(cur.stream, margin, cur.y, "CASH OUTFLOW", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            int oi = 0;
            for (var entry : outflowByCategory.entrySet()) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{entry.getKey(), fmt(entry.getValue())}, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, oi, kvRightAlign);
                oi++;
            }
            if (outflowByCategory.isEmpty()) {
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"No outflow recorded", fmt(0.0)}, PDType1Font.HELVETICA_OBLIQUE, Color.WHITE, Color.GRAY, false, 0, kvRightAlign);
            }
            ensureRoom(cur, document, pageHeight, margin, rowHeight);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"TOTAL OUTFLOW", fmt(totalOutflow)}, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, kvRightAlign);
            cur.y -= 14;

            // ---- TRANSACTIONS ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 3);
            cur.y = drawLine(cur.stream, margin, cur.y, "TRANSACTIONS", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, txnColWidths, txnHeaders, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, txnRightAlign);
            int sno = 1, rowIdx = 0;
            for (CashExpense e : summary.getExpenses()) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                String[] values = {
                        String.valueOf(sno++),
                        str(e.getDate()),
                        "OUT",
                        e.getCategory() == null ? "-" : e.getCategory().name(),
                        safe(e.getRecipientName()),
                        safe(e.getSentVia()),
                        fmt(e.getAmount())
                };
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, txnColWidths, values, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, rowIdx, txnRightAlign);
                rowIdx++;
            }
            if (summary.getExpenses().isEmpty()) {
                cur.y -= 4;
                cur.y = drawLine(cur.stream, margin, cur.y, "No transactions for this day.", PDType1Font.HELVETICA_OBLIQUE, 9, Color.GRAY, 16);
            }
            cur.y -= 14;

            // ---- LOAN COLLECTIONS ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 3);
            cur.y = drawLine(cur.stream, margin, cur.y, "LOAN COLLECTIONS", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, loanHeaders, PDType1Font.HELVETICA_BOLD, PdfTableWriter.HEADER_COLOR, Color.WHITE, false, 0, loanRightAlign);
            int lsno = 1, lRowIdx = 0;
            for (Payment p : collections) {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                Customer c = p.getCustomerId() == null ? null : customerById.get(p.getCustomerId());
                String[] values = {
                        String.valueOf(lsno++),
                        c != null ? safe(c.getName()) : "-",
                        p.getCustomerId() == null ? "-" : String.valueOf(p.getCustomerId()),
                        fmt(p.getAmount()),
                        "-",
                        safe(p.getCollectedBy())
                };
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, values, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, lRowIdx, loanRightAlign);
                lRowIdx++;
            }
            if (collections.isEmpty()) {
                cur.y -= 4;
                cur.y = drawLine(cur.stream, margin, cur.y, "No loan collections for this day.", PDType1Font.HELVETICA_OBLIQUE, 9, Color.GRAY, 16);
            } else {
                ensureRoom(cur, document, pageHeight, margin, rowHeight);
                String[] loanTotals = {"TOTAL", "", "", fmt(loanCollections), "", ""};
                cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, loanColWidths, loanTotals, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, loanRightAlign);
            }
            cur.y -= 14;

            // ---- CASH POSITION ----
            ensureRoom(cur, document, pageHeight, margin, rowHeight * 4);
            cur.y = drawLine(cur.stream, margin, cur.y, "CASH POSITION", PDType1Font.HELVETICA_BOLD, 12, Color.DARK_GRAY, 18);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"Opening", fmt(summary.getOpeningBalance())}, PDType1Font.HELVETICA, Color.WHITE, Color.BLACK, false, 0, kvRightAlign);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"+ Inflow", fmt(totalInflow)}, PDType1Font.HELVETICA, Color.WHITE, PdfTableWriter.PAID_COLOR, false, 1, kvRightAlign);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"- Outflow", fmt(totalOutflow)}, PDType1Font.HELVETICA, Color.WHITE, PdfTableWriter.PENDING_COLOR, false, 0, kvRightAlign);
            cur.y = drawTableRow(cur.stream, margin, cur.y, rowHeight, kvColWidths, new String[]{"Closing", fmt(summary.getClosingBalance())}, PDType1Font.HELVETICA_BOLD, TOTALS_ROW_COLOR, Color.BLACK, false, 0, kvRightAlign);

            cur.y -= 10;
            ensureRoom(cur, document, pageHeight, margin, 16);
            drawLine(cur.stream, margin, cur.y, "Generated by KR Finance", PDType1Font.HELVETICA_OBLIQUE, 8, Color.GRAY, 12);
            cur.stream.close();

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate cash ledger PDF", e);
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
     *
     * rightAlignCols marks which columns hold numbers (by column index, not by sniffing each
     * cell's content) so the header label sits right-aligned in exactly the same spot as the
     * data below it — a numeric column's heading and its values line up instead of the heading
     * drifting to the left of right-aligned numbers.
     */
    private float drawTableRow(PDPageContentStream stream, float margin, float cursorY, float rowHeight, float[] colWidths,
                                String[] values, org.apache.pdfbox.pdmodel.font.PDFont font, Color bg, Color textColor,
                                boolean isStatus, int rowIndex, boolean[] rightAlignCols) throws IOException {
        float x = margin;
        float totalWidth = 0;
        for (float w : colWidths) totalWidth += w;

        boolean isHeader = bg != null && bg.equals(PdfTableWriter.HEADER_COLOR);
        boolean isTotalsRow = bg != null && bg.equals(TOTALS_ROW_COLOR);
        Color rowBg;
        if (isHeader || isTotalsRow) {
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

        float fontSize = 9f;
        for (int i = 0; i < values.length && i < colWidths.length; i++) {
            String v = values[i] == null ? "" : values[i];
            int maxChars = Math.max(3, (int) (colWidths[i] / 5.0));
            if (v.length() > maxChars) v = v.substring(0, maxChars - 1) + "...";
            String sanitized = sanitize(v);

            boolean rightAlign = rightAlignCols != null && i < rightAlignCols.length && rightAlignCols[i];
            float textX;
            if (rightAlign) {
                float textWidth = font.getStringWidth(sanitized) / 1000f * fontSize;
                textX = x + colWidths[i] - textWidth - 6;
                if (textX < x + 2) textX = x + 2; // never let a too-wide value spill into the previous column
            } else {
                textX = x + 4;
            }

            stream.beginText();
            stream.setFont(font, fontSize);
            stream.setNonStrokingColor(textColor);
            stream.newLineAtOffset(textX, cursorY - rowHeight + 8);
            stream.showText(sanitized);
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
                    new String[]{"S.No", "Date", "Customer", "Type", "Amount", "Collected By"},
                    new int[]{6, 14, 28, 14, 14, 24},
                    new boolean[]{true, false, false, false, true, false});

            double total = 0;
            int sno = 1;
            for (Payment p : payments) {
                String customerName = customerRepository.findById(p.getCustomerId())
                        .map(Customer::getName).orElse("#" + p.getCustomerId());
                writer.addRow(new String[]{String.valueOf(sno++), str(p.getDate()), safe(customerName), str(p.getType()), fmt(p.getAmount()), safe(p.getCollectedBy())},
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
                    new String[]{"S.No", "Name", "Mobile", "Status", "Next Due", "Pending Amount"},
                    new int[]{6, 22, 18, 14, 14, 26},
                    new boolean[]{true, false, false, false, false, true});

            double total = 0;
            int sno = 1;
            for (Customer c : customers) {
                boolean overdue = c.getStatus() == CustomerStatus.Running && c.getNextDueDate() != null
                        && c.getNextDueDate().isBefore(LocalDate.now());
                Color bg = overdue ? PdfTableWriter.PENDING_COLOR : PdfTableWriter.WHITE;
                Color text = overdue ? Color.WHITE : Color.BLACK;
                writer.addRow(new String[]{String.valueOf(sno++), safe(c.getName()), safe(c.getMobile()), str(c.getStatus()), str(c.getNextDueDate()), fmt(c.getPendingAmount())},
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
                    new String[]{"S.No", "Name", "Finance Amt", "Total Amt", "Total Paid", "Recovery %"},
                    new int[]{6, 22, 17, 17, 17, 21},
                    new boolean[]{true, false, true, true, true, true});

            int sno = 1;
            for (Customer c : customers) {
                double totalAmount = c.getTotalAmount() == null ? 0 : c.getTotalAmount();
                double totalPaid = c.getTotalPaid() == null ? 0 : c.getTotalPaid();
                double recoveryPct = totalAmount > 0 ? (totalPaid / totalAmount) * 100.0 : 0.0;
                Color bg = recoveryPct >= 100 ? PdfTableWriter.PAID_COLOR
                        : recoveryPct > 0 ? PdfTableWriter.PARTIAL_COLOR : PdfTableWriter.PENDING_COLOR;
                writer.addRow(new String[]{String.valueOf(sno++), safe(c.getName()), fmt(c.getFinanceAmount()), fmt(totalAmount), fmt(totalPaid), String.format("%.1f%%", recoveryPct)},
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
                    new String[]{"S.No", "Date", "Customer", "Type", "Amount", "Collected By"},
                    new int[]{6, 14, 28, 14, 14, 24},
                    new boolean[]{true, false, false, false, true, false});

            double total = 0;
            int sno = 1;
            for (Payment p : payments) {
                String customerName = customerRepository.findById(p.getCustomerId())
                        .map(Customer::getName).orElse("#" + p.getCustomerId());
                writer.addRow(new String[]{String.valueOf(sno++), str(p.getDate()), safe(customerName), str(p.getType()), fmt(p.getAmount()), safe(p.getCollectedBy())},
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
