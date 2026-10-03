package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

/** A category as transactions, rules, budgets and reports show it. */
@Serializable
data class CategoryReference(
    val id: String,
    val name: String,
    /** "#RRGGBB". */
    val color: String,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val color: String,
    val transactionCount: Int = 0,
    val ruleCount: Int = 0,
    val isFallback: Boolean = false,
    val builtInReason: String? = null,
) {
    val reference get() = CategoryReference(id, name, color)
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val format: StatementFormat,
    /** Deleted (hidden) transactions included: only an account with none can be deleted. */
    val transactionCount: Int = 0,
)

@Serializable
data class BudgetReference(
    val id: String,
    val name: String,
    val status: Status = Status.Active,
)

@Serializable
data class Budget(
    val id: String,
    val name: String,
    val description: String = "",
    val status: Status = Status.Active,
    val period: BudgetPeriod = BudgetPeriod.Total,
    /** Custom periods only, "yyyy-MM-dd…". */
    val startDate: String? = null,
    val endDate: String? = null,
    val merchant: String? = null,
    val categories: List<CategoryReference> = emptyList(),
    val totalAmount: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalRemaining: Double = 0.0,
    val percentageUsed: Double = 0.0,
)

@Serializable
data class Transaction(
    val id: String,
    /** "2026-09-15T00:00:00Z": a calendar day at midnight UTC. */
    val date: String,
    val amount: Double,
    val description: String,
    val category: CategoryReference,
    val categoryLocked: Boolean = false,
    val hidden: Boolean = false,
    val transactionType: TransactionType? = null,
    val accountId: String,
    val accountName: String = "",
    val accountType: AccountType? = null,
    val flow: Flow = Flow.Spending,
    val flowLocked: Boolean = false,
    val budgetId: String? = null,
    val budget: BudgetReference? = null,
) {
    /** The calendar day, as the API stores it. */
    val day get() = isoDay(date)
}

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
)

@Serializable
data class MonthlyCategoryValues(
    /** "Sep 2026". */
    val month: String,
    val total: Double,
    /** Spending per category, by category name. */
    val categoryValues: Map<String, Double> = emptyMap(),
)

@Serializable
data class CategorizedReport(
    val categories: List<CategoryReference> = emptyList(),
    val monthlyData: List<MonthlyCategoryValues> = emptyList(),
    val averageMonthlyTotal: Double = 0.0,
    /** Current-month report only: last month's spending up to the same day of the month. */
    val previousMonthToDateTotal: Double? = null,
)

@Serializable
data class CashFlowMonth(
    val month: String,
    val income: Double,
    val spending: Double,
    /** Income minus spending: negative when more went out than came in. */
    val net: Double,
    /** Moved into savings accounts minus moved out; 0 when nothing is recorded on one. */
    val savingsChange: Double = 0.0,
)

@Serializable
data class CashFlowReport(
    val months: List<CashFlowMonth> = emptyList(),
    val averageMonthlyIncome: Double = 0.0,
    val averageMonthlySpending: Double = 0.0,
    val averageMonthlyNet: Double = 0.0,
    val averageMonthlySavingsChange: Double = 0.0,
    val averagedMonths: Int = 0,
    val averagesIncludeCurrentMonth: Boolean = false,
) {
    /** Whether the savings accounts changed in any month: nothing recorded is not the same as nothing saved. */
    val hasSavingsChange get() = months.any { it.savingsChange != 0.0 }
}

@Serializable
data class BreakdownPeriod(
    val start: String,
    val end: String,
    /** "Sep 1" (a week's Monday), "Sep 2026", "Q3 2026", "2026". */
    val label: String,
    val count: Int,
    val spending: Double,
    val income: Double,
    val cumulativeSpending: Double,
    val cumulativeIncome: Double,
)

@Serializable
data class MerchantTotal(
    val name: String,
    val count: Int,
    val spending: Double,
)

@Serializable
data class Breakdown(
    val interval: BreakdownInterval,
    val count: Int,
    /** Spending minus refunds. */
    val spending: Double,
    val refunds: Double,
    val income: Double,
    val averageSpending: Double,
    val averageIncome: Double,
    val averagedPeriods: Int,
    val periods: List<BreakdownPeriod> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val merchantCount: Int = 0,
)

@Serializable
data class CurrentUser(val username: String)

@Serializable
data class SignedOutSessions(val count: Int)

@Serializable
data class ImportResult(
    val read: Int,
    val added: Int,
    /** Rows already stored by an earlier import. */
    val skipped: Int,
)

@Serializable
data class RulesResult(
    /** Transactions the rules were applied to. */
    val checked: Int,
    /** Transactions whose category or flow changed. */
    val updated: Int,
)

@Serializable
data class CategoryRule(
    val id: String,
    val keyword: String,
    val category: CategoryReference,
    val position: Int,
)

@Serializable
data class CategoryRulePreview(
    /** Transactions whose category would change if the rule were added first and the rules re-applied. */
    val count: Int,
    /** Up to five of their descriptions. */
    val examples: List<String> = emptyList(),
)
