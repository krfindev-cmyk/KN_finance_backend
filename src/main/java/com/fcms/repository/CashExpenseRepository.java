package com.fcms.repository;

import com.fcms.model.CashExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CashExpenseRepository extends JpaRepository<CashExpense, Long> {
    List<CashExpense> findByDateOrderByCreatedAtDesc(LocalDate date);
    List<CashExpense> findByDateBefore(LocalDate date);
    List<CashExpense> findByDateBetweenOrderByDateDesc(LocalDate start, LocalDate end);
}
