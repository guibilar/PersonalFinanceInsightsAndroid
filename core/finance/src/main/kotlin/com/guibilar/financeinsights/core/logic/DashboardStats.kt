package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.CategorizedReport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** The month a report label ("Sep 2026") stands for, or null for a label in another format. */
fun monthOf(label: String): YearMonth? {
    val parts = label.split(' ')
    if (parts.size != 2) return null
    val month = MONTH_NAMES.indexOf(parts[0])
    val year = parts[1].takeIf { it.length == 4 }?.toIntOrNull() ?: return null
    return if (month >= 0) YearMonth.of(year, month + 1) else null
}

fun spentThisMonth(currentMonth: CategorizedReport): Double = currentMonth.monthlyData.firstOrNull()?.total ?: 0.0

/**
 * Percentage change from [previous] to [current], or null when there is nothing to compare with: no figure, or one at
 * or below zero (refunds outweighing the spending), against which a percentage reads the wrong way round.
 */
fun percentChange(current: Double, previous: Double?): Double? =
    if (previous == null || previous <= 0.0) null else (current - previous) / previous * 100

/**
 * The part of last month the API compares this month with: day 1 up to today's day of the month, or the whole
 * month when it is shorter. "Aug 1–5" on September 5th, "Feb 1–28" on March 30th.
 */
fun previousMonthToDateLabel(today: LocalDate): String {
    val previous = YearMonth.from(today).minusMonths(1)
    val lastDay = minOf(today.dayOfMonth, previous.lengthOfMonth())
    val month = previous.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return if (lastDay == 1) "$month 1" else "$month 1–$lastDay"
}

/** The "Spent this month" comparison: "12% more than Aug 1–5". Null when there is nothing to compare with. */
data class SpentHint(val text: String, val up: Boolean, val worse: Boolean)

/** In the words of [Change]: within 1% reads "about the same as Aug 1–5", not "0% more". */
fun spentHint(currentMonth: CategorizedReport, today: LocalDate): SpentHint? {
    val previous = currentMonth.previousMonthToDateTotal ?: return null
    val change = Change.of(spentThisMonth(currentMonth), previous) ?: return null
    return SpentHint(change.than(previousMonthToDateLabel(today)), up = change.up, worse = change.up)
}

/**
 * Positions of the months that are over, to average: the running month would pull the average down (and
 * future-dated rows don't belong in it either). When no month is over yet (January), all of them.
 */
fun finishedMonths(labels: List<String>, today: LocalDate): List<Int> {
    val current = YearMonth.from(today)
    val finished = labels.indices.filter { monthOf(labels[it])?.isBefore(current) == true }
    return finished.ifEmpty { labels.indices.toList() }
}

/** What the monthly average is taken over, in the same terms as [finishedMonths]. */
fun averageCaption(labels: List<String>, today: LocalDate): String {
    val current = YearMonth.from(today)
    val finished = labels.count { (monthOf(it) ?: current).isBefore(current) }
    if (finished > 0) return "Over $finished finished month${if (finished == 1) "" else "s"}"
    return if (labels.isNotEmpty()) "This month so far" else "No spending yet"
}

data class CategoryShare(val category: String, val amount: Double, /** 0–100. */ val percent: Double)

/**
 * Categories of the month, largest first, with their share of what was spent in them. A category whose refunds
 * outweigh its spending (a return) is left out of the total as well as the list.
 */
fun categoryShares(currentMonth: CategorizedReport): List<CategoryShare> {
    val spent = currentMonth.monthlyData.firstOrNull()?.categoryValues.orEmpty().filter { it.value > 0 }
    val total = spent.values.sum()
    return spent.map { (category, amount) -> CategoryShare(category, amount, if (total > 0) amount / total * 100 else 0.0) }
        .sortedByDescending { it.amount }
}

data class CategoryAverage(
    val category: String,
    /** Spending per finished month (a month without any counts as 0). */
    val average: Double,
    /** What the running month has added so far. */
    val thisMonth: Double,
    /** This month against the average, in percent; null when the average is 0. */
    val change: Double?,
)

/**
 * Each category's monthly average over the finished months, with this month next to it, largest average first. None
 * while no month of the year is over (January): the running month would be compared with itself.
 */
fun categoryAverages(year: CategorizedReport, today: LocalDate): List<CategoryAverage> {
    val months = year.monthlyData
    val current = YearMonth.from(today)
    if (months.none { monthOf(it.month)?.isBefore(current) == true }) return emptyList()
    val finished = finishedMonths(months.map { it.month }, today).map { months[it] }
    val running = months.firstOrNull { monthOf(it.month) == current }
    return months.flatMap { it.categoryValues.keys }.distinct()
        .map { category ->
            val total = finished.sumOf { it.categoryValues[category] ?: 0.0 }
            val average = if (finished.isNotEmpty()) total / finished.size else 0.0
            val thisMonth = running?.categoryValues?.get(category) ?: 0.0
            CategoryAverage(category, average, thisMonth, percentChange(thisMonth, average))
        }
        .filter { it.average > 0 || it.thisMonth > 0 }
        .sortedWith(compareByDescending<CategoryAverage> { it.average }.thenByDescending { it.thisMonth })
}

/** Average monthly spending over the finished months, across the given categories (all of them by default). */
fun monthlyAverage(year: CategorizedReport, today: LocalDate, categories: Set<String>? = null): Double {
    val months = finishedMonths(year.monthlyData.map { it.month }, today)
    if (months.isEmpty()) return 0.0
    val total = months.sumOf { index ->
        year.monthlyData[index].categoryValues.filterKeys { categories == null || it in categories }.values.sum()
    }
    return total / months.size
}

fun budgetsOverLimit(budgets: List<Budget>): Int = budgets.count { it.percentageUsed >= 100 }
