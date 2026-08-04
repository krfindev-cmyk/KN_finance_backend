package com.fcms.dto;

import com.fcms.model.PaymentType;

import java.time.LocalDate;

public class PaymentEditRequest {
    private Double amount;
    private LocalDate date;
    private String notes;
    private PaymentType type;
    private String editedBy;
    private String reason;

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public PaymentType getType() { return type; }
    public void setType(PaymentType type) { this.type = type; }
    public String getEditedBy() { return editedBy; }
    public void setEditedBy(String editedBy) { this.editedBy = editedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
