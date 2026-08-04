package com.fcms.repository;

import com.fcms.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findAllByOrderByDateTimeDesc();
    List<AuditEntry> findAllByCustomerNameOrderByDateTimeDesc(String customerName);
}
