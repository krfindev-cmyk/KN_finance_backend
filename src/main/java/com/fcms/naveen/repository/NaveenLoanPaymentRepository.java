package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenLoanPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NaveenLoanPaymentRepository extends JpaRepository<NaveenLoanPayment, Long> {
    List<NaveenLoanPayment> findByLoanIdOrderByDateDesc(Long loanId);
    List<NaveenLoanPayment> findByLoanIdAndDate(Long loanId, LocalDate date);
    List<NaveenLoanPayment> findByDate(LocalDate date);
}
