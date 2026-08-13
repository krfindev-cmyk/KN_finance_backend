package com.fcms.naveen.service;

import com.fcms.naveen.dto.SupplierSummary;
import com.fcms.naveen.model.NaveenSupplier;
import com.fcms.naveen.model.NaveenSupplierPayment;
import com.fcms.naveen.model.NaveenSupplierPurchase;
import com.fcms.naveen.repository.NaveenSupplierPaymentRepository;
import com.fcms.naveen.repository.NaveenSupplierPurchaseRepository;
import com.fcms.naveen.repository.NaveenSupplierRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class NaveenSupplierService {

    private final NaveenSupplierRepository supplierRepository;
    private final NaveenSupplierPurchaseRepository purchaseRepository;
    private final NaveenSupplierPaymentRepository paymentRepository;

    public NaveenSupplierService(NaveenSupplierRepository supplierRepository, NaveenSupplierPurchaseRepository purchaseRepository,
                                  NaveenSupplierPaymentRepository paymentRepository) {
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
    }

    public NaveenSupplier addSupplier(NaveenSupplier supplier) {
        supplier.setCreatedAt(LocalDateTime.now());
        return supplierRepository.save(supplier);
    }

    public List<SupplierSummary> listAll() {
        return supplierRepository.findAll().stream()
                .map(this::summarize)
                .sorted(Comparator.comparing((SupplierSummary s) -> s.getBalance()).reversed())
                .toList();
    }

    public SupplierSummary getOne(Long supplierId) {
        NaveenSupplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        return summarize(supplier);
    }

    private SupplierSummary summarize(NaveenSupplier supplier) {
        List<NaveenSupplierPurchase> purchases = purchaseRepository.findBySupplierIdOrderByDateDesc(supplier.getId());
        List<NaveenSupplierPayment> payments = paymentRepository.findBySupplierIdOrderByDateDesc(supplier.getId());
        double totalPurchases = purchases.stream().mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
        double totalPaid = payments.stream().mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();

        SupplierSummary summary = new SupplierSummary();
        summary.setSupplier(supplier);
        summary.setTotalPurchases(totalPurchases);
        summary.setTotalPaid(totalPaid);
        summary.setBalance(totalPurchases - totalPaid);
        summary.setPurchases(purchases);
        summary.setPayments(payments);
        return summary;
    }

    public NaveenSupplierPurchase addPurchase(NaveenSupplierPurchase purchase, String createdBy) {
        if (purchase.getAmount() == null && purchase.getQty() != null && purchase.getRate() != null) {
            purchase.setAmount(purchase.getQty() * purchase.getRate());
        }
        purchase.setCreatedBy(createdBy);
        purchase.setCreatedAt(LocalDateTime.now());
        return purchaseRepository.save(purchase);
    }

    public NaveenSupplierPayment addPayment(NaveenSupplierPayment payment, String createdBy) {
        payment.setCreatedBy(createdBy);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    public double totalOutstandingBalance() {
        return supplierRepository.findAll().stream()
                .mapToDouble(s -> summarize(s).getBalance())
                .sum();
    }
}
