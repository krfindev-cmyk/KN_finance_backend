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

    public NaveenSupplier updateSupplier(Long id, NaveenSupplier updated) {
        NaveenSupplier existing = supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        existing.setName(updated.getName());
        existing.setMobile(updated.getMobile());
        existing.setAddress(updated.getAddress());
        return supplierRepository.save(existing);
    }

    public void deleteSupplier(Long id) {
        purchaseRepository.deleteAll(purchaseRepository.findBySupplierIdOrderByDateDesc(id));
        paymentRepository.deleteAll(paymentRepository.findBySupplierIdOrderByDateDesc(id));
        supplierRepository.deleteById(id);
    }

    public NaveenSupplierPurchase updatePurchase(Long id, NaveenSupplierPurchase updated) {
        NaveenSupplierPurchase existing = purchaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found"));
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        if (updated.getItem() != null) existing.setItem(updated.getItem());
        if (updated.getQty() != null) existing.setQty(updated.getQty());
        if (updated.getRate() != null) existing.setRate(updated.getRate());
        if (updated.getAmount() != null) {
            existing.setAmount(updated.getAmount());
        } else if (updated.getQty() != null && updated.getRate() != null) {
            existing.setAmount(updated.getQty() * updated.getRate());
        }
        existing.setNotes(updated.getNotes());
        return purchaseRepository.save(existing);
    }

    public void deletePurchase(Long id) {
        purchaseRepository.deleteById(id);
    }

    public NaveenSupplierPayment updatePayment(Long id, NaveenSupplierPayment updated) {
        NaveenSupplierPayment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        if (updated.getAmount() != null) existing.setAmount(updated.getAmount());
        existing.setNotes(updated.getNotes());
        return paymentRepository.save(existing);
    }

    public void deletePayment(Long id) {
        paymentRepository.deleteById(id);
    }

    public double totalOutstandingBalance() {
        return supplierRepository.findAll().stream()
                .mapToDouble(s -> summarize(s).getBalance())
                .sum();
    }

    public double totalPurchasesAll() {
        return supplierRepository.findAll().stream().mapToDouble(s -> summarize(s).getTotalPurchases()).sum();
    }

    public double totalPaidAll() {
        return supplierRepository.findAll().stream().mapToDouble(s -> summarize(s).getTotalPaid()).sum();
    }
}
