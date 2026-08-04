package com.fcms.controller;

import com.fcms.model.CustomerStatus;
import com.fcms.model.FinanceType;
import com.fcms.service.ExcelExportService;
import com.fcms.service.PdfReportService;
import com.fcms.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final PdfReportService pdfReportService;
    private final ExcelExportService excelExportService;

    public ReportController(ReportService reportService, PdfReportService pdfReportService,
                             ExcelExportService excelExportService) {
        this.reportService = reportService;
        this.pdfReportService = pdfReportService;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/summary")
    public Map<String, Object> orgSummary() {
        return reportService.orgSummary();
    }

    @GetMapping("/customer/{id}")
    public Map<String, Object> customerReport(@PathVariable Long id) {
        return reportService.customerReport(id);
    }

    // ---------- PDF exports ----------

    @GetMapping("/pdf/customer-statement/{customerId}")
    public ResponseEntity<byte[]> customerStatementPdf(@PathVariable Long customerId) {
        return pdfResponse(pdfReportService.customerStatement(customerId), "customer-statement-" + customerId + ".pdf");
    }

    @GetMapping("/pdf/receipt/{paymentId}")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long paymentId) {
        return pdfResponse(pdfReportService.receipt(paymentId), "receipt-" + paymentId + ".pdf");
    }

    @GetMapping("/pdf/daily-collection")
    public ResponseEntity<byte[]> dailyCollectionPdf(@RequestParam(required = false) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return pdfResponse(pdfReportService.dailyCollection(d), "daily-collection-" + d + ".pdf");
    }

    @GetMapping("/pdf/weekly-collection")
    public ResponseEntity<byte[]> weeklyCollectionPdf(@RequestParam(required = false) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return pdfResponse(pdfReportService.weeklyCollection(d), "weekly-collection-" + d + ".pdf");
    }

    @GetMapping("/pdf/monthly-collection")
    public ResponseEntity<byte[]> monthlyCollectionPdf(@RequestParam(required = false) Integer year,
                                                         @RequestParam(required = false) Integer month) {
        YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
        return pdfResponse(pdfReportService.monthlyCollection(ym.getYear(), ym.getMonthValue()), "monthly-collection-" + ym + ".pdf");
    }

    @GetMapping("/pdf/pending")
    public ResponseEntity<byte[]> pendingPdf() {
        return pdfResponse(pdfReportService.pendingReport(), "pending-report.pdf");
    }

    @GetMapping("/pdf/recovery")
    public ResponseEntity<byte[]> recoveryPdf() {
        return pdfResponse(pdfReportService.recoveryReport(), "recovery-report.pdf");
    }

    @GetMapping("/pdf/ledger")
    public ResponseEntity<byte[]> ledgerPdf() {
        return pdfResponse(pdfReportService.ledger(), "ledger.pdf");
    }

    // ---------- Excel exports ----------

    @GetMapping("/excel/customers")
    public ResponseEntity<byte[]> customersExcel(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) FinanceType financeType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) Boolean overdue) {
        byte[] data = excelExportService.exportCustomers(q, status, financeType, paymentStatus, overdue);
        return excelResponse(data, "customers.xlsx");
    }

    @GetMapping("/excel/payments")
    public ResponseEntity<byte[]> paymentsExcel(@RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to) {
        byte[] data = excelExportService.exportPayments(from, to);
        return excelResponse(data, "payments.xlsx");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private ResponseEntity<byte[]> excelResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
