package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

/** The kind of an account; each account has one and it can't change. */
@Serializable
enum class AccountType { Chequing, Savings, CreditCard }

/** How a bank lays out its statement files; Manual accounts have none, their transactions are added by hand. */
@Serializable
enum class StatementFormat { Cibc, Neo, Manual }

@Serializable
enum class TransactionType { Credit, Debit }

/**
 * What a transaction means for the reports. Money out can be Spending, Transfer or Ignored; money in can be Refund,
 * Income, Transfer or Ignored.
 */
@Serializable
enum class Flow(val label: String) {
    Spending("Spending"),
    Refund("Refund"),
    Income("Income"),
    Transfer("Transfer"),
    Ignored("Ignored"),
}

@Serializable
enum class Status { Active, Inactive }

/** Which transactions count towards a budget's spending, by their date. */
@Serializable
enum class BudgetPeriod { Total, Monthly, Yearly, Custom }

/** How long each period of a breakdown is. Weeks start on Monday. */
@Serializable
enum class BreakdownInterval(val one: String, val many: String) {
    Week("week", "weeks"),
    Month("month", "months"),
    Quarter("quarter", "quarters"),
    Year("year", "years"),
}

@Serializable
enum class TransactionSortField { Date, Description, Amount, Category }

@Serializable
enum class SortDirection { Ascending, Descending }
