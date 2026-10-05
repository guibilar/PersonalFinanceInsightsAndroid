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
data class TagReference(val id: String, val name: String)

/** A free label across categories, with how many visible transactions carry it. */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    val transactionCount: Int = 0,
) {
    val reference get() = TagReference(id, name)
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val format: StatementFormat,
    /** Deleted (hidden) transactions included: only an account with none can be deleted. */
    val transactionCount: Int = 0,
    /** The day of its latest transaction (deleted ones included), "2026-09-30T00:00:00Z"; null without any. */
    val lastTransactionDate: String? = null,
    val plan: RegisteredPlan = RegisteredPlan.None,
    /** The plan's contribution room for [contributionRoomYear], as entered. */
    val contributionRoom: Double? = null,
    val contributionRoomYear: Int? = null,
    /** This year's transfers in: for a registered plan, its contributions. */
    val depositedThisYear: Double = 0.0,
    /** This year's transfers out: for a TFSA, room again next January. */
    val withdrawnThisYear: Double = 0.0,
    /** This year's room less this year's deposits; null until this year's room is entered. */
    val roomLeft: Double? = null,
    /** The latest balance entered. */
    val balance: AccountBalance? = null,
)

/** What an account held (a card: owed) at the end of a day, as its bank or broker showed it. */
@Serializable
data class AccountBalance(
    val id: String,
    /** "2026-09-30T00:00:00Z". */
    val date: String,
    val amount: Double,
) {
    val day get() = isoDay(date)
}

@Serializable
data class NetWorthAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    val plan: RegisteredPlan = RegisteredPlan.None,
    /** Today's balance (a card: what it owes); null until one is entered. */
    val balance: Double? = null,
    val balanceDate: String? = null,
    /** Transactions since [balanceDate] moved it. */
    val movedSince: Boolean = false,
    val isLiability: Boolean = false,
)

@Serializable
data class NetWorthMonth(
    /** "Sep 2026". */
    val month: String,
    val date: String,
    val assets: Double,
    val liabilities: Double,
    val netWorth: Double,
    /** Fewer than today's: an account's history starts later. */
    val accountsCounted: Int = 0,
)

/** What everything is worth today and at each of the last twelve month ends. */
@Serializable
data class NetWorthReport(
    val assets: Double = 0.0,
    /** What the cards owe, positive. */
    val liabilities: Double = 0.0,
    val netWorth: Double = 0.0,
    val accounts: List<NetWorthAccount> = emptyList(),
    /** Oldest first. */
    val months: List<NetWorthMonth> = emptyList(),
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
    /** A note of one's own. */
    val note: String? = null,
    /** By name. */
    val tags: List<TagReference> = emptyList(),
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
    /** The account's value at the end of the statement's period, saved as its balance that day (Wealthsimple statements). */
    val balance: Double? = null,
    /** The day of [balance]: "2026-05-31T00:00:00Z". */
    val balanceDate: String? = null,
) {
    /** "Imported 1 new transaction (2 already imported); balance on May 31, 2026: $3,845.03". */
    val message: String get() = importSummary(listOf(this))
}

/**
 * What several files' imports added together: "Imported 4 new transactions from 2 files (1 already imported)", and the
 * latest balance a statement gave.
 */
fun importSummary(results: List<ImportResult>): String {
    val added = results.sumOf { it.added }
    val skipped = results.sumOf { it.skipped }
    val noun = if (added == 1) "transaction" else "transactions"
    val files = if (results.size > 1) " from ${results.size} files" else ""
    val already = if (skipped > 0) " ($skipped already imported)" else ""
    val latest = results.filter { it.balance != null && it.balanceDate != null }.maxByOrNull { isoDay(it.balanceDate!!) }
    val worth = latest?.let {
        "; balance on ${java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US).format(isoDay(it.balanceDate!!))}: " +
            com.guibilar.financeinsights.core.logic.Money.format(it.balance!!)
    } ?: ""
    return "Imported $added new $noun$files$already$worth"
}

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

@Serializable
data class FlowRule(val id: String, val keyword: String, val flow: Flow)
