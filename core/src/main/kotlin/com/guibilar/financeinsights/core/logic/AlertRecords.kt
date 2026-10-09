package com.guibilar.financeinsights.core.logic

import java.time.LocalDate
import java.time.YearMonth

/**
 * The alerts already sent that no check can need again, so the record of them doesn't grow for ever: a budget's month
 * or year gone by, price news past its window, a review of a month before last, and import reminders since replaced by
 * a later import. Keys it can't read are kept.
 */
fun staleAlertKeys(keys: Set<String>, today: LocalDate): Set<String> {
    val latestImport = keys.filter { it.startsWith("import:") }.mapNotNull { importDay(it) }.maxOrNull()
    return keys.filterTo(mutableSetOf()) { key ->
        when {
            key.startsWith("price:") -> day(key.substringAfterLast(':'))?.isBefore(today.minusDays(PRICE_NEWS_DAYS)) == true
            key.startsWith("review:") -> month(key.removePrefix("review:"))?.isBefore(YearMonth.from(today).minusMonths(1)) == true
            key.startsWith("import:") -> importDay(key)?.let { latestImport != null && it.isBefore(latestImport) } == true
            // A budget's: "<id>:2026-10", "<id>:2026", or "<id>:all" (custom dates, kept).
            else -> key.substringAfter(':', "").let { period ->
                month(period)?.isBefore(YearMonth.from(today)) ?: period.toIntOrNull()?.let { it < today.year } ?: false
            }
        }
    }
}

private fun importDay(key: String) = day(key.removePrefix("import:").substringBefore(':'))

private fun day(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()

private fun month(text: String) = runCatching { YearMonth.parse(text) }.getOrNull()
