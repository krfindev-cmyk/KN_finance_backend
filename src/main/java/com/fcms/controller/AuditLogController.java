package com.fcms.controller;

import com.fcms.model.AuditEntry;
import com.fcms.model.Customer;
import com.fcms.service.AuditService;
import com.fcms.service.AuthService;
import com.fcms.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditService auditService;
    private final AuthService authService;
    private final CustomerService customerService;

    public AuditLogController(AuditService auditService, AuthService authService, CustomerService customerService) {
        this.auditService = auditService;
        this.authService = authService;
        this.customerService = customerService;
    }

    @GetMapping
    public List<AuditEntry> getAll(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required to view the audit log");
        }
        return auditService.getAll();
    }

    /**
     * Edit history for a single customer (profile-field edits + payment edits combined),
     * shown on the Customer Detail page. Any logged-in user (Admin or Staff) may view it,
     * since it's read-only here — only Admin can perform the edits themselves.
     */
    @GetMapping("/customer/{customerId}")
    public List<AuditEntry> getForCustomer(@PathVariable Long customerId,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (authService.resolveToken(token).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        Customer customer = customerService.getById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        return auditService.getForCustomer(customer.getName());
    }
}
