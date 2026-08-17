package com.fcms.controller;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.model.CashExpense;
import com.fcms.service.AuthService;
import com.fcms.service.CashLedgerService;
import com.fcms.service.PdfReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cash-ledger")
public class CashLedgerController {

    private final CashLedgerService cashLedgerService;
    private final AuthService authService;
    private final PdfReportService pdfReportService;

    public CashLedgerController(CashLedgerService cashLedgerService, AuthService authService, PdfReportService pdfReportService) {
        this.cashLedgerService = cashLedgerService;
        this.authService = authService;
        this.pdfReportService = pdfReportService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping("/summary")
    public CashLedgerSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashLedgerService.summary(date);
    }

    @PostMapping("/expenses")
    public CashExpense addExpense(@RequestBody CashExpense expense,
                                   @RequestParam(defaultValue = "system") String createdBy,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashLedgerService.addExpense(expense, createdBy);
    }

    @PutMapping("/expenses/{id}")
    public CashExpense updateExpense(@PathVariable Long id, @RequestBody CashExpense expense,
                                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashLedgerService.updateExpense(id, expense);
    }

    @DeleteMapping("/expenses/{id}")
    public void deleteExpense(@PathVariable Long id,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        cashLedgerService.deleteExpense(id);
    }

    @PutMapping("/balance")
    public CashLedgerSummary setBalance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam double targetBalance,
                                         @RequestParam(defaultValue = "system") String editedBy,
                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return cashLedgerService.setBalance(date, targetBalance, editedBy);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        LocalDate d = date == null ? LocalDate.now() : date;
        byte[] data = pdfReportService.cashLedger(d);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("cash-ledger-" + d + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
