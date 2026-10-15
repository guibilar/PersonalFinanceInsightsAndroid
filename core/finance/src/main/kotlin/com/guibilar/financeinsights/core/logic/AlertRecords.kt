package com.guibilar.financeinsights.core.logic

import java.time.LocalDate
import java.time.YearMonth

/**
 * The alerts already sent that no check can need again, so the record of them doesn't grow for ever: a review of a
 * month before last. The price changes and import reminders ("price:" and "import:" keys) are server alerts since T68,
 * and the budget warnings ("<id>:<period>" keys) since T71, so their old records all go. Keys it can't read are kept.
 */
fun staleAlertKeys(keys: Set<String>, today: LocalDate): Set<String> =
    keys.filterTo(mutableSetOf()) { key ->
        when {
            key.startsWith("price:") || key.startsWith("import:") -> true
            key.startsWith("review:") -> month(key.removePrefix("review:"))?.isBefore(YearMonth.from(today).minusMonths(1)) == true
            // A budget warning's: "<id>:2026-10", "<id>:2026", or "<id>:all".
            else -> key.contains(':')
        }
    }

private fun month(text: String) = runCatching { YearMonth.parse(text) }.getOrNull()
