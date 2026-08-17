package com.fcms.naveen.controller;

import com.fcms.naveen.dto.SupplierSummary;
import com.fcms.naveen.model.NaveenSupplier;
import com.fcms.naveen.model.NaveenSupplierPayment;
import com.fcms.naveen.model.NaveenSupplierPurchase;
import com.fcms.naveen.service.NaveenSupplierService;
import com.fcms.service.AuthService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/naveen/suppliers")
public class NaveenSupplierController {

    private final NaveenSupplierService supplierService;
    private final AuthService authService;

    public NaveenSupplierController(NaveenSupplierService supplierService, AuthService authService) {
        this.supplierService = supplierService;
        this.authService = authService;
    }

    private void requireAdmin(String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!authService.isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required for this action");
        }
    }

    @GetMapping
    public List<SupplierSummary> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.listAll();
    }

    @GetMapping("/{id}")
    public SupplierSummary getOne(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.getOne(id);
    }

    @PostMapping
    public NaveenSupplier addSupplier(@RequestBody NaveenSupplier supplier, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.addSupplier(supplier);
    }

    @PostMapping("/{id}/purchases")
    public NaveenSupplierPurchase addPurchase(@PathVariable Long id, @RequestBody NaveenSupplierPurchase purchase,
                                               @RequestParam(defaultValue = "system") String createdBy,
                                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        purchase.setSupplierId(id);
        return supplierService.addPurchase(purchase, createdBy);
    }

    @PostMapping("/{id}/payments")
    public NaveenSupplierPayment addPayment(@PathVariable Long id, @RequestBody NaveenSupplierPayment payment,
                                             @RequestParam(defaultValue = "system") String createdBy,
                                             @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        payment.setSupplierId(id);
        return supplierService.addPayment(payment, createdBy);
    }

    @PutMapping("/{id}")
    public NaveenSupplier updateSupplier(@PathVariable Long id, @RequestBody NaveenSupplier supplier,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.updateSupplier(id, supplier);
    }

    @DeleteMapping("/{id}")
    public void deleteSupplier(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        supplierService.deleteSupplier(id);
    }

    @PutMapping("/purchases/{purchaseId}")
    public NaveenSupplierPurchase updatePurchase(@PathVariable Long purchaseId, @RequestBody NaveenSupplierPurchase purchase,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.updatePurchase(purchaseId, purchase);
    }

    @DeleteMapping("/purchases/{purchaseId}")
    public void deletePurchase(@PathVariable Long purchaseId, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        supplierService.deletePurchase(purchaseId);
    }

    @PutMapping("/payments/{paymentId}")
    public NaveenSupplierPayment updatePayment(@PathVariable Long paymentId, @RequestBody NaveenSupplierPayment payment,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        return supplierService.updatePayment(paymentId, payment);
    }

    @DeleteMapping("/payments/{paymentId}")
    public void deletePayment(@PathVariable Long paymentId, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        requireAdmin(authHeader);
        supplierService.deletePayment(paymentId);
    }
}
