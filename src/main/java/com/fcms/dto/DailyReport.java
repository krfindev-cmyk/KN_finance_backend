package com.fcms.dto;

import java.time.LocalDate;
import java.util.List;

/** Full Daily Report payload: today's org-wide summary stats plus every customer's row. */
public class DailyReport {
    private LocalDate date;
    private Double totalToCollect;
    private Double totalCollected;
    private Double totalNotCollected;
    private int paidCount;
    private int notPaidCount;
    private int partialCount;
    private int advanceCount;
    private List<DailyReportRow> rows;

    public DailyReport() {}

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Double getTotalToCollect() { return totalToCollect; }
    public void setTotalToCollect(Double totalToCollect) { this.totalToCollect = totalToCollect; }
    public Double getTotalCollected() { return totalCollected; }
    public void setTotalCollected(Double totalCollected) { this.totalCollected = totalCollected; }
    public Double getTotalNotCollected() { return totalNotCollected; }
    public void setTotalNotCollected(Double totalNotCollected) { this.totalNotCollected = totalNotCollected; }
    public int getPaidCount() { return paidCount; }
    public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
    public int getNotPaidCount() { return notPaidCount; }
    public void setNotPaidCount(int notPaidCount) { this.notPaidCount = notPaidCount; }
    public int getPartialCount() { return partialCount; }
    public void setPartialCount(int partialCount) { this.partialCount = partialCount; }
    public int getAdvanceCount() { return advanceCount; }
    public void setAdvanceCount(int advanceCount) { this.advanceCount = advanceCount; }
    public List<DailyReportRow> getRows() { return rows; }
    public void setRows(List<DailyReportRow> rows) { this.rows = rows; }
}
