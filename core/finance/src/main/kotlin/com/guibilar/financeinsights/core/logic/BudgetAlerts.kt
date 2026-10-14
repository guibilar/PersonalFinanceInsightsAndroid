package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Status
import java.time.LocalDate
import java.time.YearMonth

/** A budget that crossed a threshold: what to tell the person, once. */
data class BudgetAlert(
    val budgetId: String,
    /** The budget and its current period: a monthly budget can alert again next month. */
    val key: String,
    /** Always 80 (nearly used up): the server alert "budget-over" tells when a budget is used up (T68). */
    val level: Int,
    val title: String,
    val text: String,
)

/** The thresholds a budget alerts at, in order. */
val ALERT_LEVELS = listOf(80, 100)

/**
 * The warnings due now: each active budget between 80% and 100% that hasn't alerted at 80 or more in its current period
 * ([sent] is the highest level already sent, by key). A budget at 100% or more gets no local alert: the server alert
 * "budget-over" (Hestia.Api, Alerts module) tells it, and the phone polls it (T68). There is no server rule for 80% yet.
 */
fun budgetAlerts(budgets: List<Budget>, today: LocalDate, sent: Map<String, Int>): List<BudgetAlert> =
    budgets.filter { it.status == Status.Active && it.totalAmount > 0 }.mapNotNull { budget ->
        val level = ALERT_LEVELS.lastOrNull { budget.percentageUsed >= it } ?: return@mapNotNull null
        if (level >= 100) return@mapNotNull null
        val key = "${budget.id}:${periodKey(budget.period, today)}"
        if (level <= (sent[key] ?: 0)) return@mapNotNull null
        val period = when (budget.period) {
            BudgetPeriod.Monthly -> " this month"
            BudgetPeriod.Yearly -> " this year"
            else -> ""
        }
        BudgetAlert(
            budgetId = budget.id,
            key = key,
            level = level,
            title = "${budget.name} is nearly used up",
            text = "${Money.format(budget.totalSpent)} of ${Money.format(budget.totalAmount)}$period " +
                "(${Money.percentUsed(budget.percentageUsed)}): ${Money.format(budget.totalRemaining)} left.",
        )
    }

/**
 * Budgets back under a level they alerted at in their current period (a refund, or the amount raised): the level
 * each has now (0 under 80), by key, so crossing again is told again. A custom budget's period never ends, so without
 * this, raising its amount after it was used up would never alert again.
 */
fun budgetLevelsDropped(budgets: List<Budget>, today: LocalDate, sent: Map<String, Int>): Map<String, Int> =
    budgets.filter { it.status == Status.Active && it.totalAmount > 0 }.mapNotNull { budget ->
        val key = "${budget.id}:${periodKey(budget.period, today)}"
        val sentLevel = sent[key] ?: return@mapNotNull null
        val level = ALERT_LEVELS.lastOrNull { budget.percentageUsed >= it } ?: 0
        if (level < sentLevel) key to level else null
    }.toMap()

private fun periodKey(period: BudgetPeriod, today: LocalDate) = when (period) {
    BudgetPeriod.Monthly -> YearMonth.from(today).toString()
    BudgetPeriod.Yearly -> today.year.toString()
    else -> "all"
}
