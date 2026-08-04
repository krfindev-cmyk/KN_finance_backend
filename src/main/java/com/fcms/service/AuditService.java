package com.fcms.service;

import com.fcms.model.AuditEntry;
import com.fcms.repository.AuditEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;

    public AuditService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    public void log(String entity, Long entityId, String customerName, String field,
                     Object oldValue, Object newValue, String editedBy, String reason) {
        AuditEntry entry = new AuditEntry();
        entry.setEntity(entity);
        entry.setEntityId(entityId);
        entry.setCustomerName(customerName);
        entry.setField(field);
        entry.setOldValue(oldValue == null ? "" : String.valueOf(oldValue));
        entry.setNewValue(newValue == null ? "" : String.valueOf(newValue));
        entry.setEditedBy(editedBy == null ? "system" : editedBy);
        entry.setDateTime(LocalDateTime.now());
        entry.setReason(reason == null ? "" : reason);
        auditEntryRepository.save(entry);
    }

    public List<AuditEntry> getAll() {
        return auditEntryRepository.findAllByOrderByDateTimeDesc();
    }

    /** Edit history (both customer-profile edits and payment edits) for a single customer, by name. */
    public List<AuditEntry> getForCustomer(String customerName) {
        return auditEntryRepository.findAllByCustomerNameOrderByDateTimeDesc(customerName);
    }
}
