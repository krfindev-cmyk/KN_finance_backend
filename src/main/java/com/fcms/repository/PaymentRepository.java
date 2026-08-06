package com.fcms.repository;

import com.fcms.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCustomerIdOrderByDateDesc(Long customerId);
    List<Payment> findByDate(LocalDate date);
    List<Payment> findByDateBetween(LocalDate start, LocalDate end);
    /** Normally at most one, but returns a List so any pre-existing duplicate-day data self-heals instead of erroring. */
    List<Payment> findByCustomerIdAndDate(Long customerId, LocalDate date);
}
