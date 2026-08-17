package com.fcms.naveen.service;

import com.fcms.naveen.dto.BorrowingSummary;
import com.fcms.naveen.model.NaveenBorrowing;
import com.fcms.naveen.model.NaveenBorrowingRepayment;
import com.fcms.naveen.repository.NaveenBorrowingRepaymentRepository;
import com.fcms.naveen.repository.NaveenBorrowingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class NaveenBorrowingService {

    private final NaveenBorrowingRepository borrowingRepository;
    private final NaveenBorrowingRepaymentRepository repaymentRepository;

    public NaveenBorrowingService(NaveenBorrowingRepository borrowingRepository, NaveenBorrowingRepaymentRepository repaymentRepository) {
        this.borrowingRepository = borrowingRepository;
        this.repaymentRepository = repaymentRepository;
    }

    public NaveenBorrowing addBorrowing(NaveenBorrowing borrowing) {
        borrowing.setCreatedAt(LocalDateTime.now());
        return borrowingRepository.save(borrowing);
    }

    public List<BorrowingSummary> listAll() {
        return borrowingRepository.findAll().stream()
                .map(this::summarize)
                .sorted(Comparator.comparing((BorrowingSummary s) -> s.getBalance()).reversed())
                .toList();
    }

    public BorrowingSummary getOne(Long borrowingId) {
        NaveenBorrowing borrowing = borrowingRepository.findById(borrowingId)
                .orElseThrow(() -> new IllegalArgumentException("Borrowing not found"));
        return summarize(borrowing);
    }

    private BorrowingSummary summarize(NaveenBorrowing borrowing) {
        List<NaveenBorrowingRepayment> repayments = repaymentRepository.findByBorrowingIdOrderByDateDesc(borrowing.getId());
        double principal = borrowing.getAmount() == null ? 0 : borrowing.getAmount();
        double interestPct = borrowing.getInterestPercent() == null ? 0 : borrowing.getInterestPercent();
        double totalPayable = principal + (principal * interestPct / 100.0);
        double totalRepaid = repayments.stream().mapToDouble(r -> r.getAmount() == null ? 0 : r.getAmount()).sum();

        BorrowingSummary summary = new BorrowingSummary();
        summary.setBorrowing(borrowing);
        summary.setTotalPayable(totalPayable);
        summary.setTotalRepaid(totalRepaid);
        summary.setBalance(totalPayable - totalRepaid);
        summary.setRepayments(repayments);
        return summary;
    }

    public NaveenBorrowingRepayment addRepayment(NaveenBorrowingRepayment repayment, String createdBy) {
        repayment.setCreatedBy(createdBy);
        repayment.setCreatedAt(LocalDateTime.now());
        return repaymentRepository.save(repayment);
    }

    public NaveenBorrowing updateBorrowing(Long id, NaveenBorrowing updated) {
        NaveenBorrowing existing = borrowingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Borrowing not found"));
        existing.setLenderName(updated.getLenderName());
        existing.setMobile(updated.getMobile());
        if (updated.getAmount() != null) existing.setAmount(updated.getAmount());
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        existing.setInterestPercent(updated.getInterestPercent());
        existing.setNotes(updated.getNotes());
        return borrowingRepository.save(existing);
    }

    public void deleteBorrowing(Long id) {
        repaymentRepository.deleteAll(repaymentRepository.findByBorrowingIdOrderByDateDesc(id));
        borrowingRepository.deleteById(id);
    }

    public NaveenBorrowingRepayment updateRepayment(Long id, NaveenBorrowingRepayment updated) {
        NaveenBorrowingRepayment existing = repaymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repayment not found"));
        if (updated.getDate() != null) existing.setDate(updated.getDate());
        if (updated.getAmount() != null) existing.setAmount(updated.getAmount());
        existing.setNotes(updated.getNotes());
        return repaymentRepository.save(existing);
    }

    public void deleteRepayment(Long id) {
        repaymentRepository.deleteById(id);
    }

    public double totalOutstandingBalance() {
        return borrowingRepository.findAll().stream()
                .mapToDouble(b -> summarize(b).getBalance())
                .sum();
    }

    public double totalPayableAll() {
        return borrowingRepository.findAll().stream().mapToDouble(b -> summarize(b).getTotalPayable()).sum();
    }

    public double totalRepaidAll() {
        return borrowingRepository.findAll().stream().mapToDouble(b -> summarize(b).getTotalRepaid()).sum();
    }
}
