package com.fcms.naveen.controller;

import com.fcms.naveen.dto.BorrowingSummary;
import com.fcms.naveen.model.NaveenBorrowing;
import com.fcms.naveen.model.NaveenBorrowingRepayment;
import com.fcms.naveen.service.NaveenBorrowingService;
import com.fcms.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/naveen/borrowings")
public class NaveenBorrowingController {

    private final NaveenBorrowingService borrowingService;
    private final AuthService authService;

    public NaveenBorrowingController(NaveenBorrowingService borrowingService, AuthService authService) {
        this.borrowingService = borrowingService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping
    public List<BorrowingSummary> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return borrowingService.listAll();
    }

    @GetMapping("/{id}")
    public BorrowingSummary getOne(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return borrowingService.getOne(id);
    }

    @PostMapping
    public NaveenBorrowing addBorrowing(@RequestBody NaveenBorrowing borrowing, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return borrowingService.addBorrowing(borrowing);
    }

    @PostMapping("/{id}/repayments")
    public NaveenBorrowingRepayment addRepayment(@PathVariable Long id, @RequestBody NaveenBorrowingRepayment repayment,
                                                  @RequestParam(defaultValue = "system") String createdBy,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        repayment.setBorrowingId(id);
        return borrowingService.addRepayment(repayment, createdBy);
    }

    @PutMapping("/{id}")
    public NaveenBorrowing updateBorrowing(@PathVariable Long id, @RequestBody NaveenBorrowing borrowing,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return borrowingService.updateBorrowing(id, borrowing);
    }

    @DeleteMapping("/{id}")
    public void deleteBorrowing(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        borrowingService.deleteBorrowing(id);
    }

    @PutMapping("/repayments/{repaymentId}")
    public NaveenBorrowingRepayment updateRepayment(@PathVariable Long repaymentId, @RequestBody NaveenBorrowingRepayment repayment,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return borrowingService.updateRepayment(repaymentId, repayment);
    }

    @DeleteMapping("/repayments/{repaymentId}")
    public void deleteRepayment(@PathVariable Long repaymentId, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        borrowingService.deleteRepayment(repaymentId);
    }
}
