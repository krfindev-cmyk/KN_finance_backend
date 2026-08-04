package com.fcms.dto;

import java.time.LocalDate;

/**
 * One day (or one week, for Weekly-type loans) of a loan's installment schedule, merged with
 * any actual Payment recorded for that date. Used to render the full "100 day" (or weekly)
 * collection timeline on the customer detail page, including days nobody ever recorded
 * anything for (shown as Missed/Pending rather than just being absent from the list).
 */
public class TimelineEntry {

    private int installmentNo;
    private LocalDate date;
    /** Paid / Partial / NotPaid / Advance if a payment exists for this date, otherwise Pending (future) or Missed (past, uncollected). */
    private String status;
    private Double amount;
    private Long paymentId;
    private boolean today;

    public TimelineEntry() {}

    public TimelineEntry(int installmentNo, LocalDate date, String status, Double amount, Long paymentId, boolean today) {
        this.installmentNo = installmentNo;
        this.date = date;
        this.status = status;
        this.amount = amount;
        this.paymentId = paymentId;
        this.today = today;
    }

    public int getInstallmentNo() { return installmentNo; }
    public void setInstallmentNo(int installmentNo) { this.installmentNo = installmentNo; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public boolean isToday() { return today; }
    public void setToday(boolean today) { this.today = today; }
}
