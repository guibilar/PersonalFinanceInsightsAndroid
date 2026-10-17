package com.guibilar.financeinsights.core.today

import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// The server sends raw values (Hestia T08), so the app keeps its own formats and its privacy mode. The rules are the
// same as the web (features/today/today-text.ts).

// Halves round away from zero, as Intl.NumberFormat does on the web: DecimalFormat's own half-even reads 2.5 as "2".
private val counts = ThreadLocal.withInitial { decimalFormat("#,##0") }
private val percents = ThreadLocal.withInitial { decimalFormat("#,##0.#") }

private fun decimalFormat(pattern: String) =
    DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }

/**
 * One Today value as the app shows it: amounts as "$1,234.56" ("$•••••" when amounts are hidden), days as
 * "Jun 30, 2026", counts as "1,234", percentages as "12%" or "12.5%". A value type that the app does not know gives
 * its text, if it has one.
 */
fun formatTodayValue(value: TodayValue): String = when (value.type) {
    TodayValueType.Money -> Money.format(value.number ?: 0.0)
    TodayValueType.Count -> counts.get().format(value.number ?: 0.0)
    TodayValueType.Percent -> "${percents.get().format(value.number ?: 0.0)}%"
    TodayValueType.Date -> value.date?.let { runCatching { formatDay(it) }.getOrNull() ?: it }.orEmpty()
    TodayValueType.Text, TodayValueType.Unknown -> value.text.orEmpty()
}

private val placeholder = Regex("""\{([A-Za-z0-9_-]+)\}""")

/**
 * A card text with each `{name}` placeholder filled from [values]. A placeholder without a value stays as it is, so a
 * contract mistake is visible and not a silent gap. Null text gives an empty string.
 */
fun fillTodayText(text: String?, values: Map<String, TodayValue>): String {
    if (text.isNullOrEmpty()) return ""
    return placeholder.replace(text) { match ->
        values[match.groupValues[1]]?.let(::formatTodayValue) ?: match.value
    }
}

/** The card's text, filled in; empty when it has none. */
val TodayCard.filledText: String get() = fillTodayText(text, values)

/** The big value of a tile; "—" when the server does not know it. */
val TodayCard.valueText: String get() = value?.let(::formatTodayValue) ?: "—"
