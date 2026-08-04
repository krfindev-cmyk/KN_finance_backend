package com.fcms.controller;

import com.fcms.dto.PaymentEditRequest;
import com.fcms.model.Payment;
import com.fcms.service.AuthService;
import com.fcms.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;

    public PaymentController(PaymentService paymentService, AuthService authService) {
        this.paymentService = paymentService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping
    public List<Payment> getAll() {
        return paymentService.getAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<Payment> getByCustomer(@PathVariable Long customerId) {
        return paymentService.getByCustomer(customerId);
    }

    @PostMapping
    public Payment create(@RequestBody Payment payment) {
        return paymentService.recordPayment(payment);
    }

    @PutMapping("/{id}")
    public Payment edit(@PathVariable Long id, @RequestBody PaymentEditRequest req,
                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return paymentService.editPayment(id, req);
    }
}
