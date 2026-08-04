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
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
