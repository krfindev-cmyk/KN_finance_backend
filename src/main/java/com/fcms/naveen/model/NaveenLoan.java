package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Money Naveen has given to someone (a receivable), collected back daily/weekly/monthly/custom. */
@Entity
@Table(name = "naveen_loans")
public class NaveenLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String borrowerName;
    private String mobile;

    @Column(length = 1000)
    private String address;

    private Double amount;
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private NaveenLoanFrequency frequency;

    private Double installmentAmount;
    private Integer totalInstallments;
    private Integer paidInstallments;

    private Double totalPaid;
    private Double pendingAmount;

    private LocalDate nextDueDate;

    /** Links multiple loans to the same person, mirroring the main KR Finance groupKey pattern. */
    private String groupKey;

    @Enumerated(EnumType.STRING)
    private NaveenLoanStatus status;

    private LocalDateTime createdAt;

    public NaveenLoan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBorrowerName() { return borrowerName; }
    public void setBorrowerName(String borrowerName) { this.borrowerName = borrowerName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public NaveenLoanFrequency getFrequency() { return frequency; }
    public void setFrequency(NaveenLoanFrequency frequency) { this.frequency = frequency; }
    public Double getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(Double installmentAmount) { this.installmentAmount = installmentAmount; }
    public Integer getTotalInstallments() { return totalInstallments; }
    public void setTotalInstallments(Integer totalInstallments) { this.totalInstallments = totalInstallments; }
    public Integer getPaidInstallments() { return paidInstallments; }
    public void setPaidInstallments(Integer paidInstallments) { this.paidInstallments = paidInstallments; }
    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }
    public Double getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(Double pendingAmount) { this.pendingAmount = pendingAmount; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public NaveenLoanStatus getStatus() { return status; }
    public void setStatus(NaveenLoanStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PostLoad
    private void applyDefaults() {
        if (paidInstallments == null) paidInstallments = 0;
        if (totalPaid == null) totalPaid = 0.0;
        if (pendingAmount == null && amount != null) pendingAmount = amount - totalPaid;
    }
}
