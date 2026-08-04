package com.fcms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String mobile;
    private String alternateMobile;

    /**
     * Links multiple loan accounts belonging to the same real-world person (e.g. someone who
     * has taken a second or third loan). Defaults to a slug of the name if not set explicitly,
     * so loans can be grouped/looked-up without requiring a full customer/loan schema split.
     * Two customer records with the same groupKey are shown as "Other Loans" on each other's detail page.
     */
    private String groupKey;

    @Column(length = 1000)
    private String address;

    private Double financeAmount;
    private Double interest;
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    private FinanceType financeType;

    private String collectionDay; // for weekly finance type

    private Double installmentAmount;
    private Integer totalInstallments;
    private Integer paidInstallments;

    private Double totalAmount;
    private Double totalPaid;
    private Double pendingAmount;
    private Double currentBalance;

    private LocalDate nextDueDate;
    private LocalDate lastPaymentDate;
    private Double lastPaymentAmount;
    /** Type (Paid/Partial/NotPaid/Advance) of the most recently recorded payment for this loan — drives the payment-status filter on the Customers list. */
    private String lastPaymentType;

    /** Estimated completion date: startDate + totalInstallments periods (days for Daily, weeks for Weekly). Recomputed server-side. */
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    private LocalDateTime createdAt;

    public Customer() {}

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getAlternateMobile() { return alternateMobile; }
    public void setAlternateMobile(String alternateMobile) { this.alternateMobile = alternateMobile; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getFinanceAmount() { return financeAmount; }
    public void setFinanceAmount(Double financeAmount) { this.financeAmount = financeAmount; }
    public Double getInterest() { return interest; }
    public void setInterest(Double interest) { this.interest = interest; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public FinanceType getFinanceType() { return financeType; }
    public void setFinanceType(FinanceType financeType) { this.financeType = financeType; }
    public String getCollectionDay() { return collectionDay; }
    public void setCollectionDay(String collectionDay) { this.collectionDay = collectionDay; }
    public Double getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(Double installmentAmount) { this.installmentAmount = installmentAmount; }
    public Integer getTotalInstallments() { return totalInstallments; }
    public void setTotalInstallments(Integer totalInstallments) { this.totalInstallments = totalInstallments; }
    public Integer getPaidInstallments() { return paidInstallments; }
    public void setPaidInstallments(Integer paidInstallments) { this.paidInstallments = paidInstallments; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }
    public Double getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(Double pendingAmount) { this.pendingAmount = pendingAmount; }
    public Double getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(Double currentBalance) { this.currentBalance = currentBalance; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }
    public Double getLastPaymentAmount() { return lastPaymentAmount; }
    public void setLastPaymentAmount(Double lastPaymentAmount) { this.lastPaymentAmount = lastPaymentAmount; }
    public String getLastPaymentType() { return lastPaymentType; }
    public void setLastPaymentType(String lastPaymentType) { this.lastPaymentType = lastPaymentType; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Self-heal older/imported rows that predate the 100-installment default (e.g. rows
     * inserted directly via SQL without totalInstallments/installmentAmount set). This only
     * fixes the in-memory object returned from a read — it doesn't rewrite the row — so the
     * "Installments Paid" stat never shows "x / null" and installment amount never shows
     * Rs. 0 for a loan that actually has a finance amount.
     */
    @PostLoad
    private void applyLoanDefaults() {
        if (totalInstallments == null || totalInstallments <= 0) {
            totalInstallments = 100;
        }
        if ((installmentAmount == null || installmentAmount <= 0) && financeAmount != null && totalInstallments > 0) {
            double base = financeAmount + (financeAmount * (interest == null ? 0 : interest) / 100.0);
            installmentAmount = Math.round((base / totalInstallments) * 100.0) / 100.0;
        }
        if (paidInstallments == null) {
            paidInstallments = 0;
        }
    }
}
