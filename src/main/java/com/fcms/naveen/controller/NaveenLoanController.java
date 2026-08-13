package com.fcms.naveen.controller;

import com.fcms.naveen.dto.LoanSummary;
import com.fcms.naveen.model.NaveenLoan;
import com.fcms.naveen.model.NaveenLoanPayment;
import com.fcms.naveen.service.NaveenLoanService;
import com.fcms.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/naveen/loans")
public class NaveenLoanController {

    private final NaveenLoanService loanService;
    private final AuthService authService;

    public NaveenLoanController(NaveenLoanService loanService, AuthService authService) {
        this.loanService = loanService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping
    public List<LoanSummary> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return loanService.listAll();
    }

    @GetMapping("/{id}")
    public LoanSummary getOne(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return loanService.getOne(id);
    }

    @PostMapping
    public NaveenLoan addLoan(@RequestBody NaveenLoan loan, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return loanService.addLoan(loan);
    }

    @PostMapping("/{id}/payments")
    public NaveenLoanPayment recordPayment(@PathVariable Long id, @RequestBody NaveenLoanPayment payment,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        payment.setLoanId(id);
        return loanService.recordPayment(payment);
    }
}
