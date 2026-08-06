package com.fcms.dto;

import com.fcms.model.CashExpense;

import java.time.LocalDate;
import java.util.List;

/**
 * One day's cash-in-hand picture: what was collected that day, what was spent/sent out,
 * and the balance — which is whatever's left over after opening balance + collected - spent,
 * and rolls forward automatically into the next day's opening balance.
 */
public class CashLedgerSummary {
    private LocalDate date;
    private double openingBalance;
    private double collectedToday;
    private double expensesToday;
    private double closingBalance;
    private List<CashExpense> expenses;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }
    public double getCollectedToday() { return collectedToday; }
    public void setCollectedToday(double collectedToday) { this.collectedToday = collectedToday; }
    public double getExpensesToday() { return expensesToday; }
    public void setExpensesToday(double expensesToday) { this.expensesToday = expensesToday; }
    public double getClosingBalance() { return closingBalance; }
    public void setClosingBalance(double closingBalance) { this.closingBalance = closingBalance; }
    public List<CashExpense> getExpenses() { return expenses; }
    public void setExpenses(List<CashExpense> expenses) { this.expenses = expenses; }
}
