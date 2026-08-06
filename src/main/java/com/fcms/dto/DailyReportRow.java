package com.fcms.dto;

/** One customer's row on the Daily Report — everything a collector needs to see for today at a glance. */
public class DailyReportRow {
    private Long customerId;
    private String name;
    private String mobile;
    private Double totalLoanAmount;
    private Double totalPaid;
    private Double balanceAmount;
    private Double dailyCollection;
    private Integer daysPaid;
    private Integer totalInstallments;
    /** Paid / Partial / NotPaid / Advance if today's already been marked, otherwise "Not Due Yet" or "Pending". */
    private String todayStatus;
    private Double todayAmount;

    public DailyReportRow() {}

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public Double getTotalLoanAmount() { return totalLoanAmount; }
    public void setTotalLoanAmount(Double totalLoanAmount) { this.totalLoanAmount = totalLoanAmount; }
    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }
    public Double getBalanceAmount() { return balanceAmount; }
    public void setBalanceAmount(Double balanceAmount) { this.balanceAmount = balanceAmount; }
    public Double getDailyCollection() { return dailyCollection; }
    public void setDailyCollection(Double dailyCollection) { this.dailyCollection = dailyCollection; }
    public Integer getDaysPaid() { return daysPaid; }
    public void setDaysPaid(Integer daysPaid) { this.daysPaid = daysPaid; }
    public Integer getTotalInstallments() { return totalInstallments; }
    public void setTotalInstallments(Integer totalInstallments) { this.totalInstallments = totalInstallments; }
    public String getTodayStatus() { return todayStatus; }
    public void setTodayStatus(String todayStatus) { this.todayStatus = todayStatus; }
    public Double getTodayAmount() { return todayAmount; }
    public void setTodayAmount(Double todayAmount) { this.todayAmount = todayAmount; }
}
