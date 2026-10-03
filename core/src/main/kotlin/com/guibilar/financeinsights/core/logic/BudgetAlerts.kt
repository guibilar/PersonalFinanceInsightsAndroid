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
    /** 80 (nearly used up) or 100 (used up or over). */
    val level: Int,
    val title: String,
    val text: String,
)

/** The thresholds a budget alerts at, in order. */
val ALERT_LEVELS = listOf(80, 100)

/**
 * The alerts due now: each active budget past a threshold it hasn't alerted at in its current period ([sent] is
 * the highest level already sent, by key). Passing 100 straight away gives one alert, not two.
 */
fun budgetAlerts(budgets: List<Budget>, today: LocalDate, sent: Map<String, Int>): List<BudgetAlert> =
    budgets.filter { it.status == Status.Active && it.totalAmount > 0 }.mapNotNull { budget ->
        val level = ALERT_LEVELS.lastOrNull { budget.percentageUsed >= it } ?: return@mapNotNull null
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
            title = if (level >= 100) "${budget.name} is used up" else "${budget.name} is nearly used up",
            text = if (budget.totalRemaining < 0) {
                "${Money.format(budget.totalSpent)} of ${Money.format(budget.totalAmount)}$period: " +
                    "${Money.format(-budget.totalRemaining)} over."
            } else {
                "${Money.format(budget.totalSpent)} of ${Money.format(budget.totalAmount)}$period " +
                    "(${Money.percentUsed(budget.percentageUsed)}): ${Money.format(budget.totalRemaining)} left."
            },
        )
    }

private fun periodKey(period: BudgetPeriod, today: LocalDate) = when (period) {
    BudgetPeriod.Monthly -> YearMonth.from(today).toString()
    BudgetPeriod.Yearly -> today.year.toString()
    else -> "all"
}
