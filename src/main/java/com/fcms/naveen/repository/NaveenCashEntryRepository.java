package com.fcms.naveen.repository;

import com.fcms.naveen.model.NaveenCashEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NaveenCashEntryRepository extends JpaRepository<NaveenCashEntry, Long> {
    List<NaveenCashEntry> findByDateOrderByCreatedAtDesc(LocalDate date);
    List<NaveenCashEntry> findByDateBefore(LocalDate date);
}
