package com.fcms.naveen.service;

import com.fcms.naveen.dto.BillDetail;
import com.fcms.naveen.dto.BillRequest;
import com.fcms.naveen.model.NaveenBill;
import com.fcms.naveen.model.NaveenBillItem;
import com.fcms.naveen.model.NaveenSupplier;
import com.fcms.naveen.model.NaveenSupplierPurchase;
import com.fcms.naveen.repository.NaveenBillItemRepository;
import com.fcms.naveen.repository.NaveenBillRepository;
import com.fcms.naveen.repository.NaveenSupplierPurchaseRepository;
import com.fcms.naveen.repository.NaveenSupplierRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Itemized vegetable purchase invoices ("Billing"). Creating a bill with a supplier chosen also
 * logs a matching NaveenSupplierPurchase for the invoice total, so the supplier's running balance
 * stays correct without needing the item-by-item breakdown duplicated on the Suppliers page.
 */
@Service
public class NaveenBillService {

    private final NaveenBillRepository billRepository;
    private final NaveenBillItemRepository billItemRepository;
    private final NaveenSupplierRepository supplierRepository;
    private final NaveenSupplierPurchaseRepository purchaseRepository;

    public NaveenBillService(NaveenBillRepository billRepository, NaveenBillItemRepository billItemRepository,
                              NaveenSupplierRepository supplierRepository, NaveenSupplierPurchaseRepository purchaseRepository) {
        this.billRepository = billRepository;
        this.billItemRepository = billItemRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseRepository = purchaseRepository;
    }

    private double lineAmount(NaveenBillItem item) {
        if (item.getAmount() != null) return item.getAmount();
        double qty = item.getQty() == null ? 0 : item.getQty();
        double rate = item.getRate() == null ? 0 : item.getRate();
        return qty * rate;
    }

    public BillDetail createBill(BillRequest req, String createdBy) {
        if (req.getItems() == null) req.setItems(java.util.List.of());
        double total = 0;
        for (NaveenBillItem item : req.getItems()) {
            item.setAmount(lineAmount(item));
            total += item.getAmount();
        }

        NaveenBill bill = new NaveenBill();
        bill.setSupplierId(req.getSupplierId());
        bill.setCustomerName(req.getCustomerName());
        bill.setDate(req.getDate() != null ? req.getDate() : LocalDate.now());
        bill.setTotalAmount(Math.round(total * 100.0) / 100.0);
        bill.setCreatedBy(createdBy);
        bill.setCreatedAt(LocalDateTime.now());

        if (req.getSupplierId() != null) {
            NaveenSupplierPurchase purchase = new NaveenSupplierPurchase();
            purchase.setSupplierId(req.getSupplierId());
            purchase.setDate(bill.getDate());
            purchase.setAmount(bill.getTotalAmount());
            purchase.setNotes("Billing invoice");
            purchase.setCreatedBy(createdBy);
            purchase.setCreatedAt(LocalDateTime.now());
            NaveenSupplierPurchase saved = purchaseRepository.save(purchase);
            bill.setPurchaseId(saved.getId());
        }

        NaveenBill savedBill = billRepository.save(bill);
        for (NaveenBillItem item : req.getItems()) {
            item.setBillId(savedBill.getId());
            billItemRepository.save(item);
        }

        return getOne(savedBill.getId());
    }

    public BillDetail updateBill(Long id, BillRequest req, String editedBy) {
        NaveenBill bill = billRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));

        if (req.getItems() == null) req.setItems(java.util.List.of());
        double total = 0;
        for (NaveenBillItem item : req.getItems()) {
            item.setAmount(lineAmount(item));
            total += item.getAmount();
        }
        double roundedTotal = Math.round(total * 100.0) / 100.0;

        bill.setSupplierId(req.getSupplierId());
        bill.setCustomerName(req.getCustomerName());
        if (req.getDate() != null) bill.setDate(req.getDate());
        bill.setTotalAmount(roundedTotal);

        // Keep the linked supplier purchase (if any) in sync with the new total/date/supplier.
        if (bill.getPurchaseId() != null) {
            if (req.getSupplierId() == null) {
                purchaseRepository.deleteById(bill.getPurchaseId());
                bill.setPurchaseId(null);
            } else {
                purchaseRepository.findById(bill.getPurchaseId()).ifPresent(p -> {
                    p.setSupplierId(req.getSupplierId());
                    p.setDate(bill.getDate());
                    p.setAmount(roundedTotal);
                    purchaseRepository.save(p);
                });
            }
        } else if (req.getSupplierId() != null) {
            NaveenSupplierPurchase purchase = new NaveenSupplierPurchase();
            purchase.setSupplierId(req.getSupplierId());
            purchase.setDate(bill.getDate());
            purchase.setAmount(roundedTotal);
            purchase.setNotes("Billing invoice");
            purchase.setCreatedBy(editedBy);
            purchase.setCreatedAt(LocalDateTime.now());
            NaveenSupplierPurchase saved = purchaseRepository.save(purchase);
            bill.setPurchaseId(saved.getId());
        }

        billRepository.save(bill);

        billItemRepository.deleteAll(billItemRepository.findByBillIdOrderByIdAsc(id));
        for (NaveenBillItem item : req.getItems()) {
            item.setId(null);
            item.setBillId(id);
            billItemRepository.save(item);
        }

        return getOne(id);
    }

    public void deleteBill(Long id) {
        NaveenBill bill = billRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        if (bill.getPurchaseId() != null) {
            purchaseRepository.deleteById(bill.getPurchaseId());
        }
        billItemRepository.deleteAll(billItemRepository.findByBillIdOrderByIdAsc(id));
        billRepository.deleteById(id);
    }

    public List<BillDetail> listAll() {
        return billRepository.findAllByOrderByDateDescIdDesc().stream().map(b -> toDetail(b)).toList();
    }

    public BillDetail getOne(Long id) {
        NaveenBill bill = billRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        return toDetail(bill);
    }

    private BillDetail toDetail(NaveenBill bill) {
        BillDetail detail = new BillDetail();
        detail.setBill(bill);
        detail.setItems(billItemRepository.findByBillIdOrderByIdAsc(bill.getId()));
        if (bill.getSupplierId() != null) {
            supplierRepository.findById(bill.getSupplierId()).ifPresent(s -> detail.setSupplierName(s.getName()));
        }
        return detail;
    }
}
