package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

/** Payments that come again and again (subscriptions, bills, rent), found in the spending. */
@Serializable
data class RecurringReport(
    /** The active and late ones by monthly cost, then the stopped ones, latest first. */
    val payments: List<RecurringPayment> = emptyList(),
    /** Of the active and late ones. */
    val monthlyTotal: Double = 0.0,
    val yearlyTotal: Double = 0.0,
) {
    val coming get() = payments.filter { it.status != RecurringStatus.Stopped }
    val stopped get() = payments.filter { it.status == RecurringStatus.Stopped }
}

@Serializable
data class RecurringPayment(
    /** The merchant: its rule's keyword, or the start of its description. */
    val name: String,
    val cadence: RecurringCadence,
    val status: RecurringStatus,
    /** The latest charge for a fixed price, the average of the last three when it varies. */
    val amount: Double,
    val variableAmount: Boolean = false,
    val firstDate: String,
    val lastDate: String,
    val lastAmount: Double,
    val nextDate: String,
    val count: Int,
    val monthlyCost: Double,
    val yearlyCost: Double,
    val category: CategoryReference? = null,
    /** A fixed price that changed with the latest charge. */
    val priceChange: PriceChange? = null,
)

@Serializable
data class PriceChange(val from: Double, val to: Double, val since: String)

/** Where this month's spending is heading. */
@Serializable
data class SpendingForecast(
    /** "October 2026". */
    val month: String,
    val daysInMonth: Int,
    /** Days after today until the month ends. */
    val daysLeft: Int,
    val spent: Double,
    val projected: Double,
    /** The average of the last complete months (up to three); null with none. */
    val usual: Double? = null,
    val usualMonths: Int = 0,
    /** Day-to-day spending a day (recurring payments left out) the days left are projected at. */
    val dailyRate: Double = 0.0,
    val upcoming: List<UpcomingCharge> = emptyList(),
    val budgets: List<BudgetForecast> = emptyList(),
) {
    val upcomingTotal get() = upcoming.sumOf { it.amount }

    /** What the days left add at the usual pace: the projection less what is spent and due. */
    val dayToDay get() = (projected - spent - upcomingTotal).coerceAtLeast(0.0)
}

@Serializable
data class UpcomingCharge(val name: String, val amount: Double, val date: String)

@Serializable
data class BudgetForecast(
    val budgetId: String,
    val name: String,
    val period: BudgetPeriod,
    val amount: Double,
    val spent: Double,
    /** Where it should end, this month or this year, at its usual pace. */
    val projected: Double,
    val projectedPercentage: Double,
)

/** Merchants whose spending landed in Other because no rule names them. */
@Serializable
data class UncategorisedReport(
    val merchants: List<UncategorisedMerchant> = emptyList(),
    /** Every transaction a rule could sort, across all of them. */
    val transactionCount: Int = 0,
)

@Serializable
data class UncategorisedMerchant(
    /** The keyword to suggest for a rule. */
    val keyword: String,
    val count: Int,
    val spending: Double,
    val examples: List<String> = emptyList(),
    val lastDate: String,
)

/** A month or a year looked back on, against the one before. */
@Serializable
data class ReviewReport(
    /** "September 2026", or "2026". */
    val label: String,
    val year: Int,
    /** Null for a year. */
    val month: Int? = null,
    val start: String,
    val end: String,
    /** False while the period is still running. */
    val complete: Boolean,
    val previousLabel: String,
    val spending: Double,
    val previousSpending: Double,
    val income: Double,
    val previousIncome: Double,
    val purchases: Int,
    val noSpendDays: Int,
    val categories: List<ReviewCategory> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val biggestPurchase: ReviewPurchase? = null,
    /** For a year: each month so far. */
    val months: List<ReviewMonth> = emptyList(),
    val recurringCount: Int = 0,
    val recurringMonthlyTotal: Double = 0.0,
    /** The first month with data, "2025-03". */
    val earliest: String? = null,
)

@Serializable
data class ReviewCategory(val category: CategoryReference, val spending: Double, val previousSpending: Double)

@Serializable
data class ReviewPurchase(val description: String, val amount: Double, val date: String, val category: CategoryReference? = null)

@Serializable
data class ReviewMonth(val label: String, val spending: Double, val income: Double)

/** One change for many transactions: only what is set changes. */
@Serializable
data class TransactionBulkUpdate(
    val transactionIds: List<String>,
    val categoryId: String? = null,
    val flow: Flow? = null,
    /** Links them to this active budget, moving any linked to another. */
    val budgetId: String? = null,
    val unlinkBudget: Boolean = false,
    val addTagId: String? = null,
    val removeTagId: String? = null,
)

@Serializable
data class BulkUpdateResult(
    val updated: Int,
    val skipped: Int = 0,
    /** Why some were skipped. */
    val notes: List<String> = emptyList(),
)
