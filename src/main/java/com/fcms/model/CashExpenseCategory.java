package com.fcms.model;

/** What a cash outflow from the day's collection was for. */
public enum CashExpenseCategory {
    PetrolAllowance,
    FoodAllowance,
    Salary,
    SentToPerson,
    Other,
    /** A manual correction to the running balance — amount can be negative (adds money back) or positive (removes it), used to fix a mistaken total rather than record a real outflow. */
    Adjustment
}
