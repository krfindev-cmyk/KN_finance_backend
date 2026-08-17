package com.fcms.naveen.service;

import com.fcms.naveen.dto.NaveenCashSummary;
import com.fcms.naveen.model.*;
import com.fcms.naveen.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The master cash ledger for Naveen's business: combines loan collections (money in),
 * supplier payments and borrowing repayments (money out), plus any manually logged entries
 * (vegetable sales, other income, refunds, other expenses) into one opening/closing balance per day.
 */
@Service
public class NaveenCashService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final NaveenCashEntryRepository cashEntryRepository;
    private final NaveenLoanPaymentRepository loanPaymentRepository;
    private final NaveenSupplierPaymentRepository supplierPaymentRepository;
    private final NaveenBorrowingRepaymentRepository borrowingRepaymentRepository;

    public NaveenCashService(NaveenCashEntryRepository cashEntryRepository, NaveenLoanPaymentRepository loanPaymentRepository,
                              NaveenSupplierPaymentRepository supplierPaymentRepository,
                              NaveenBorrowingRepaymentRepository borrowingRepaymentRepository) {
        this.cashEntryRepository = cashEntryRepository;
        this.loanPaymentRepository = loanPaymentRepository;
        this.supplierPaymentRepository = supplierPaymentRepository;
        this.borrowingRepaymentRepository = borrowingRepaymentRepository;
    }

    private double loanCollectionsOn(LocalDate date) {
        return loanPaymentRepository.findByDate(date).stream()
                .filter(p -> p.getType() != NaveenPaymentType.NotPaid)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    private double supplierPaymentsOn(LocalDate date) {
        return supplierPaymentRepository.findAll().stream()
                .filter(p -> date.equals(p.getDate()))
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount())
                .sum();
    }

    private double borrowingRepaymentsOn(LocalDate date) {
        return borrowingRepaymentRepository.findAll().stream()
                .filter(r -> date.equals(r.getDate()))
                .mapToDouble(r -> r.getAmount() == null ? 0 : r.getAmount())
                .sum();
    }

    private double netMovementBefore(LocalDate date) {
        double inflow = loanPaymentRepository.findAll().stream()
                .filter(p -> p.getDate() != null && p.getDate().isBefore(date) && p.getType() != NaveenPaymentType.NotPaid)
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
        double manualIn = cashEntryRepository.findByDateBefore(date).stream()
                .filter(e -> e.getDirection() == NaveenCashDirection.IN)
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum();
        double manualOut = cashEntryRepository.findByDateBefore(date).stream()
                .filter(e -> e.getDirection() == NaveenCashDirection.OUT)
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum();
        double supplierOut = supplierPaymentRepository.findAll().stream()
                .filter(p -> p.getDate() != null && p.getDate().isBefore(date))
                .mapToDouble(p -> p.getAmount() == null ? 0 : p.getAmount()).sum();
        double borrowingOut = borrowingRepaymentRepository.findAll().stream()
                .filter(r -> r.getDate() != null && r.getDate().isBefore(date))
                .mapToDouble(r -> r.getAmount() == null ? 0 : r.getAmount()).sum();
        return (inflow + manualIn) - (manualOut + supplierOut + borrowingOut);
    }

    public NaveenCashSummary summary(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now(IST);
        List<NaveenCashEntry> entries = cashEntryRepository.findByDateOrderByCreatedAtDesc(d);

        double loanCollections = round2(loanCollectionsOn(d));
        double otherIncome = round2(entries.stream().filter(e -> e.getDirection() == NaveenCashDirection.IN)
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());
        double supplierPayments = round2(supplierPaymentsOn(d));
        double borrowingRepayments = round2(borrowingRepaymentsOn(d));
        double otherExpense = round2(entries.stream().filter(e -> e.getDirection() == NaveenCashDirection.OUT)
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount()).sum());

        double totalInflow = round2(loanCollections + otherIncome);
        double totalOutflow = round2(supplierPayments + borrowingRepayments + otherExpense);
        double openingBalance = round2(netMovementBefore(d));
        double closingBalance = round2(openingBalance + totalInflow - totalOutflow);

        NaveenCashSummary summary = new NaveenCashSummary();
        summary.setDate(d);
        summary.setOpeningBalance(openingBalance);
        summary.setLoanCollections(loanCollections);
        summary.setOtherIncome(otherIncome);
        summary.setSupplierPayments(supplierPayments);
        summary.setBorrowingRepayments(borrowingRepayments);
        summary.setOtherExpense(otherExpense);
        summary.setTotalInflow(totalInflow);
        summary.setTotalOutflow(totalOutflow);
        summary.setClosingBalance(closingBalance);
        summary.setEntries(entries);
        return summary;
    }

    public NaveenCashEntry addEntry(NaveenCashEntry entry, String createdBy) {
        if (entry.getDate() == null) entry.setDate(LocalDate.now(IST));
        entry.setCreatedBy(createdBy);
        entry.setCreatedAt(LocalDateTime.now());
        return cashEntryRepository.save(entry);
    }

    public void deleteEntry(Long id) {
        cashEntryRepository.deleteById(id);
    }

    /** Every expense entry ever logged, most recent first — the plain "daily spending" list shown on the Expenses page. */
    public List<NaveenCashEntry> listAll() {
        return cashEntryRepository.findAllByOrderByDateDescCreatedAtDesc();
    }

    public NaveenCashEntry updateEntry(Long id, NaveenCashEntry updated) {
        NaveenCashEntry existing = cashEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense entry not found"));
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        if (updated.getDirection() != null) existing.setDirection(updated.getDirection());
        if (updated.getCategory() != null) existing.setCategory(updated.getCategory());
        if (updated.getAmount() != null) existing.setAmount(updated.getAmount());
        existing.setNotes(updated.getNotes());
        return cashEntryRepository.save(existing);
    }

    /** Total of every expense (OUT) entry ever logged — the headline number on the Expenses page. */
    public double totalExpenses() {
        return round2(listAll().stream()
                .filter(e -> e.getDirection() == NaveenCashDirection.OUT)
                .mapToDouble(e -> e.getAmount() == null ? 0 : e.getAmount())
                .sum());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
