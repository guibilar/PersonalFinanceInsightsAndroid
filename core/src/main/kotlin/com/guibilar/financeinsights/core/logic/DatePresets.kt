package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.TransactionCriteria
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date filter as the transactions API takes it: inclusive days, either end left open. */
data class DateRange(val start: LocalDate? = null, val end: LocalDate? = null) {
    val startDate get() = start?.toString()
    val endDate get() = end?.toString()
}

/** The quick date filters, as on the web: whole months before the running one, so none is cut short. */
enum class DatePreset(val label: String) {
    ThisMonth("This month"),
    LastMonth("Last month"),
    Last3Months("Last 3 months"),
    Last6Months("Last 6 months"),
    Last12Months("Last 12 months"),
    ThisYear("This year"),
    LastYear("Last year");

    /** The days it covers on [today]. The running month and year stay open-ended, so they run to today. */
    fun range(today: LocalDate): DateRange = when (this) {
        ThisMonth -> DateRange(today.withDayOfMonth(1))
        LastMonth -> wholeMonths(today, 1)
        Last3Months -> wholeMonths(today, 3)
        Last6Months -> wholeMonths(today, 6)
        Last12Months -> wholeMonths(today, 12)
        ThisYear -> DateRange(today.withDayOfYear(1))
        LastYear -> today.minusYears(1).let { DateRange(it.withDayOfYear(1), it.withMonth(12).withDayOfMonth(31)) }
    }

    private fun wholeMonths(today: LocalDate, count: Long): DateRange {
        val monthStart = today.withDayOfMonth(1)
        return DateRange(monthStart.minusMonths(count), monthStart.minusDays(1))
    }
}

/** Which choice the criteria's dates are on [today]: no dates, a preset, or any other range. */
sealed interface DateChoice {
    data object All : DateChoice
    data class Preset(val preset: DatePreset) : DateChoice
    data object Custom : DateChoice
}

fun matchDatePreset(criteria: TransactionCriteria, today: LocalDate): DateChoice {
    if (criteria.startDate == null && criteria.endDate == null) return DateChoice.All
    val preset = DatePreset.entries.firstOrNull {
        val range = it.range(today)
        range.startDate == criteria.startDate && range.endDate == criteria.endDate
    }
    return preset?.let { DateChoice.Preset(it) } ?: DateChoice.Custom
}

private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** The days a range covers: "Jun 1 – Aug 31, 2026", "Dec 1, 2025 – Feb 28, 2026", "Sep 1 – today". */
fun describeRange(range: DateRange): String {
    val start = range.start ?: return range.end?.let { "Until ${monthDayYear.format(it)}" } ?: ""
    val end = range.end ?: return "${monthDay.format(start)} – today"
    val startText = if (start.year == end.year) monthDay.format(start) else monthDayYear.format(start)
    return "$startText – ${monthDayYear.format(end)}"
}

fun TransactionCriteria.dateRange() = DateRange(startDate?.let(LocalDate::parse), endDate?.let(LocalDate::parse))
