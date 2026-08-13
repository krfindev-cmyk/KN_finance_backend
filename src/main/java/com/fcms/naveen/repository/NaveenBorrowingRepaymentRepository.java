package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenBorrowingRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenBorrowingRepaymentRepository extends JpaRepository<NaveenBorrowingRepayment, Long> {
    List<NaveenBorrowingRepayment> findByBorrowingIdOrderByDateDesc(Long borrowingId);
}
