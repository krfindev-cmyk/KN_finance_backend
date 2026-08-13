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

    public double totalOutstandingBalance() {
        return borrowingRepository.findAll().stream()
                .mapToDouble(b -> summarize(b).getBalance())
                .sum();
    }
}
