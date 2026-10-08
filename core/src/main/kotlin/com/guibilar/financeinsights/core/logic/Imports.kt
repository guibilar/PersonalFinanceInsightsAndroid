package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.isoDay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

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
        "; balance on ${longDay.format(isoDay(it.balanceDate!!))}: ${Money.format(it.balance!!)}"
    } ?: ""
    return "Imported $added new $noun$files$already$worth"
}
