package com.fcms.naveen.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Money Naveen has borrowed from someone (a lender/financier) and needs to repay over time. */
@Entity
@Table(name = "naveen_borrowings")
public class NaveenBorrowing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String lenderName;
    private String mobile;
    private Double amount;
    private LocalDate date;

    /** Optional simple interest percentage; leave null/0 to ignore interest entirely. */
    private Double interestPercent;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    public NaveenBorrowing() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLenderName() { return lenderName; }
    public void setLenderName(String lenderName) { this.lenderName = lenderName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getInterestPercent() { return interestPercent; }
    public void setInterestPercent(Double interestPercent) { this.interestPercent = interestPercent; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
