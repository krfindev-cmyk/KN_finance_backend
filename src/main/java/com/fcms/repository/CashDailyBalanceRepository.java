package com.fcms.repository;

import com.fcms.model.CashDailyBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface CashDailyBalanceRepository extends JpaRepository<CashDailyBalance, Long> {
    Optional<CashDailyBalance> findByDate(LocalDate date);
}
