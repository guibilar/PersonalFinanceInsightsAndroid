package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

/** The kind of an account; each account has one and it can't change. */
@Serializable
enum class AccountType {
    Chequing, Savings, CreditCard,

    /** Its value moves with the market, not only with its transactions. */
    Investment;

    /** Savings and investments: money put away, which can be held in a registered plan. */
    val holdsSavings get() = this == Savings || this == Investment
}

/** The Canadian registered plan a savings or investment account is held in. */
@Serializable
enum class RegisteredPlan(val label: String) {
    None("Not registered"), Tfsa("TFSA"), Rrsp("RRSP"), Fhsa("FHSA")
}

/** How a bank lays out its statement files; Manual accounts have none, their transactions are added by hand. */
@Serializable
enum class StatementFormat { Cibc, Neo, Wealthsimple, Manual }

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

/** How often a recurring payment comes. */
@Serializable
enum class RecurringCadence(val label: String, val perYear: Int) {
    Weekly("every week", 52),
    Biweekly("every 2 weeks", 26),
    Monthly("every month", 12),
    Quarterly("every 3 months", 4),
    Yearly("every year", 1),
}

/** Active: on schedule. Late: past due, not by a whole period yet. Stopped: no charge for more than a period. */
@Serializable
enum class RecurringStatus { Active, Late, Stopped }
