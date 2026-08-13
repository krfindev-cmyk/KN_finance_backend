package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenLoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NaveenLoanRepository extends JpaRepository<NaveenLoan, Long> {
    List<NaveenLoan> findAllByGroupKeyOrderByDateAsc(String groupKey);
}
