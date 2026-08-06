package com.fcms.controller;

import com.fcms.dto.CashLedgerSummary;
import com.fcms.model.CashExpense;
import com.fcms.service.AuthService;
import com.fcms.service.CashLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cash-ledger")
public class CashLedgerController {

    private final CashLedgerService cashLedgerService;
    private final AuthService authService;

    public CashLedgerController(CashLedgerService cashLedgerService, AuthService authService) {
        this.cashLedgerService = cashLedgerService;
        this.authService = authService;
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

    @DeleteMapping("/expenses/{id}")
    public void deleteExpense(@PathVariable Long id,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        cashLedgerService.deleteExpense(id);
    }
}
