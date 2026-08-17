package com.fcms.naveen.seed;

import com.fcms.naveen.dto.BillRequest;
import com.fcms.naveen.model.*;
import com.fcms.naveen.repository.NaveenBillRepository;
import com.fcms.naveen.repository.NaveenBorrowingRepository;
import com.fcms.naveen.repository.NaveenLoanRepository;
import com.fcms.naveen.repository.NaveenSupplierRepository;
import com.fcms.naveen.service.NaveenBillService;
import com.fcms.naveen.service.NaveenBorrowingService;
import com.fcms.naveen.service.NaveenCashService;
import com.fcms.naveen.service.NaveenLoanService;
import com.fcms.naveen.service.NaveenSupplierService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds a handful of realistic sample entries into Naveen's business section on first run
 * (only when the tables are empty), so the section isn't blank the first time it's opened.
 */
@Component
@Order(10)
public class NaveenDataSeeder implements CommandLineRunner {

    private final NaveenSupplierRepository supplierRepository;
    private final NaveenBorrowingRepository borrowingRepository;
    private final NaveenLoanRepository loanRepository;
    private final NaveenBillRepository billRepository;
    private final NaveenSupplierService supplierService;
    private final NaveenBorrowingService borrowingService;
    private final NaveenLoanService loanService;
    private final NaveenCashService cashService;
    private final NaveenBillService billService;

    public NaveenDataSeeder(NaveenSupplierRepository supplierRepository, NaveenBorrowingRepository borrowingRepository,
                             NaveenLoanRepository loanRepository, NaveenBillRepository billRepository,
                             NaveenSupplierService supplierService, NaveenBorrowingService borrowingService,
                             NaveenLoanService loanService, NaveenCashService cashService, NaveenBillService billService) {
        this.supplierRepository = supplierRepository;
        this.borrowingRepository = borrowingRepository;
        this.loanRepository = loanRepository;
        this.billRepository = billRepository;
        this.supplierService = supplierService;
        this.borrowingService = borrowingService;
        this.loanService = loanService;
        this.cashService = cashService;
        this.billService = billService;
    }

    @Override
    public void run(String... args) {
        boolean firstRun = supplierRepository.count() == 0;
        if (firstRun) seedSuppliers();
        if (borrowingRepository.count() == 0) seedBorrowings();
        if (loanRepository.count() == 0) seedLoans();
        if (firstRun && billRepository.count() == 0) seedBills();
        seedCashEntries();
    }

    /** One sample itemized invoice against the first seeded supplier, so the Billing tab isn't empty. */
    private void seedBills() {
        List<NaveenSupplier> suppliers = supplierRepository.findAll();
        if (suppliers.isEmpty()) return;
        NaveenSupplier supplier = suppliers.get(0);

        BillRequest req = new BillRequest();
        req.setSupplierId(supplier.getId());
        req.setDate(LocalDate.now().minusDays(1));
        NaveenBillItem tomato = new NaveenBillItem();
        tomato.setItem("Tomato");
        tomato.setQty(50.0);
        tomato.setRate(30.0);
        NaveenBillItem onion = new NaveenBillItem();
        onion.setItem("Onion");
        onion.setQty(20.0);
        onion.setRate(40.0);
        NaveenBillItem carrot = new NaveenBillItem();
        carrot.setItem("Carrot");
        carrot.setQty(15.0);
        carrot.setRate(35.0);
        req.setItems(List.of(tomato, onion, carrot));

        billService.createBill(req, "Naveen");
    }

    private void seedSuppliers() {
        LocalDate today = LocalDate.now();

        NaveenSupplier kumar = new NaveenSupplier();
        kumar.setName("Kumar Vegetables");
        kumar.setMobile("9876543210");
        kumar.setAddress("Wholesale Market, Koyambedu");
        kumar = supplierService.addSupplier(kumar);

        addPurchase(kumar.getId(), today.minusDays(12), "Tomato", 500.0, 30.0);
        addPayment(kumar.getId(), today.minusDays(11), 2000.0);
        addPayment(kumar.getId(), today.minusDays(10), 3000.0);
        addPayment(kumar.getId(), today.minusDays(9), 5000.0);
        addPurchase(kumar.getId(), today.minusDays(3), "Onion", 300.0, 40.0);
        addPayment(kumar.getId(), today.minusDays(2), 4000.0);

        NaveenSupplier selvam = new NaveenSupplier();
        selvam.setName("Selvam Traders");
        selvam.setMobile("9865321470");
        selvam.setAddress("Vegetable Market, Ukkadam");
        selvam = supplierService.addSupplier(selvam);

        addPurchase(selvam.getId(), today.minusDays(6), "Potato", 400.0, 25.0);
        addPayment(selvam.getId(), today.minusDays(5), 5000.0);
    }

    private void addPurchase(Long supplierId, LocalDate date, String item, double qty, double rate) {
        NaveenSupplierPurchase p = new NaveenSupplierPurchase();
        p.setSupplierId(supplierId);
        p.setDate(date);
        p.setItem(item);
        p.setQty(qty);
        p.setRate(rate);
        supplierService.addPurchase(p, "Naveen");
    }

    private void addPayment(Long supplierId, LocalDate date, double amount) {
        NaveenSupplierPayment p = new NaveenSupplierPayment();
        p.setSupplierId(supplierId);
        p.setDate(date);
        p.setAmount(amount);
        supplierService.addPayment(p, "Naveen");
    }

    private void seedBorrowings() {
        LocalDate today = LocalDate.now();

        NaveenBorrowing ravi = new NaveenBorrowing();
        ravi.setLenderName("Ravi");
        ravi.setMobile("9944556677");
        ravi.setAmount(100000.0);
        ravi.setDate(today.minusDays(13));
        ravi = borrowingService.addBorrowing(ravi);

        addRepayment(ravi.getId(), today.minusDays(12), 10000.0);
        addRepayment(ravi.getId(), today.minusDays(8), 5000.0);
        addRepayment(ravi.getId(), today.minusDays(3), 10000.0);

        NaveenBorrowing financier = new NaveenBorrowing();
        financier.setLenderName("Murugan Finance");
        financier.setMobile("9922334455");
        financier.setAmount(50000.0);
        financier.setInterestPercent(12.0);
        financier.setDate(today.minusDays(20));
        financier = borrowingService.addBorrowing(financier);

        addRepayment(financier.getId(), today.minusDays(10), 15000.0);
    }

    private void addRepayment(Long borrowingId, LocalDate date, double amount) {
        NaveenBorrowingRepayment r = new NaveenBorrowingRepayment();
        r.setBorrowingId(borrowingId);
        r.setDate(date);
        r.setAmount(amount);
        borrowingService.addRepayment(r, "Naveen");
    }

    private void seedLoans() {
        LocalDate today = LocalDate.now();

        NaveenLoan senthil = new NaveenLoan();
        senthil.setBorrowerName("Senthil");
        senthil.setMobile("9887766554");
        senthil.setAddress("Peelamedu, Coimbatore");
        senthil.setAmount(50000.0);
        senthil.setDate(today.minusDays(4));
        senthil.setFrequency(NaveenLoanFrequency.Daily);
        senthil.setInstallmentAmount(500.0);
        senthil.setTotalInstallments(100);
        senthil = loanService.addLoan(senthil);

        recordCollection(senthil.getId(), today.minusDays(3), 500.0, NaveenPaymentType.Paid);
        recordCollection(senthil.getId(), today.minusDays(2), 500.0, NaveenPaymentType.Paid);
        recordCollection(senthil.getId(), today.minusDays(1), 0.0, NaveenPaymentType.NotPaid);

        NaveenLoan geetha = new NaveenLoan();
        geetha.setBorrowerName("Geetha");
        geetha.setMobile("9812345670");
        geetha.setAddress("RS Puram, Coimbatore");
        geetha.setAmount(25000.0);
        geetha.setDate(today.minusDays(30));
        geetha.setFrequency(NaveenLoanFrequency.Weekly);
        geetha.setInstallmentAmount(3500.0);
        geetha.setTotalInstallments(8);
        geetha = loanService.addLoan(geetha);

        recordCollection(geetha.getId(), today.minusDays(23), 3500.0, NaveenPaymentType.Paid);
        recordCollection(geetha.getId(), today.minusDays(16), 3500.0, NaveenPaymentType.Paid);
        recordCollection(geetha.getId(), today.minusDays(9), 3500.0, NaveenPaymentType.Paid);
    }

    private void recordCollection(Long loanId, LocalDate date, double amount, NaveenPaymentType type) {
        NaveenLoanPayment p = new NaveenLoanPayment();
        p.setLoanId(loanId);
        p.setDate(date);
        p.setAmount(amount);
        p.setType(type);
        p.setCollectedBy("Naveen");
        loanService.recordPayment(p);
    }

    /** A couple of sample daily-spending entries, so the Expenses tab isn't empty on first load. */
    private void seedCashEntries() {
        if (!cashService.listAll().isEmpty()) return;
        LocalDate today = LocalDate.now();

        addExpense(today, "Petrol", 300.0, "Sample entry — bike fuel for deliveries");
        addExpense(today.minusDays(1), "Food", 150.0, "Sample entry — lunch");
        addExpense(today.minusDays(2), "Transport", 250.0, "Sample entry — auto to market");
    }

    private void addExpense(LocalDate date, String category, double amount, String notes) {
        NaveenCashEntry entry = new NaveenCashEntry();
        entry.setDate(date);
        entry.setDirection(NaveenCashDirection.OUT);
        entry.setCategory(category);
        entry.setAmount(amount);
        entry.setNotes(notes);
        cashService.addEntry(entry, "Naveen");
    }
}
