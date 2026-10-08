package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.isoDay
import com.guibilar.financeinsights.core.model.ReviewReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.YearMonth

/** A subscription whose fixed price changed: what to tell the person, once. */
data class PriceAlert(val key: String, val title: String, val text: String)

/** A price change is told for this long after the first charge at the new price; older ones aren't news. */
private const val PRICE_NEWS_DAYS = 30L

/** The monthly review is offered in the first week of the month, once. */
private const val REVIEW_DAYS = 7

/**
 * The price changes to tell now: of the payments still coming, each whose latest charge changed a fixed price in the
 * last month, unless already told ([sent], by key).
 */
fun priceAlerts(report: RecurringReport, today: LocalDate, sent: Map<String, Int>): List<PriceAlert> =
    report.coming.mapNotNull { payment ->
        val change = payment.priceChange ?: return@mapNotNull null
        val since = LocalDate.parse(change.since.take(10))
        if (since.isBefore(today.minusDays(PRICE_NEWS_DAYS))) return@mapNotNull null
        val key = "price:${payment.name}:${change.since.take(10)}"
        if (key in sent) return@mapNotNull null
        val up = change.to > change.from
        val yearly = (change.to - change.from) * payment.cadence.perYear
        PriceAlert(
            key = key,
            title = "${payment.name} went ${if (up) "up" else "down"}",
            text = "Now ${Money.format(change.to)} ${payment.cadence.label}, from ${Money.format(change.from)}: " +
                "${Money.format(kotlin.math.abs(yearly))} ${if (up) "more" else "less"} a year.",
        )
    }

/** The key the month before's review is told under, in the first week of a month; null later in the month. */
fun reviewKey(today: LocalDate): String? =
    if (today.dayOfMonth <= REVIEW_DAYS) "review:${YearMonth.from(today).minusMonths(1)}" else null

/** The month before in review, as a notification's title and text. */
fun reviewAlert(review: ReviewReport): Pair<String, String> {
    val change = Change.of(review.spending, review.previousSpending)
    val top = review.categories.maxByOrNull { it.spending }?.takeIf { it.spending > 0 }
    val text = buildString {
        append("${Money.format(review.spending)} spent")
        if (change != null) append(", ${change.than(review.previousLabel)}")
        append(".")
        if (top != null) append(" Most went to ${top.category.name}, ${Money.format(top.spending)}.")
    }
    return "Your ${review.label.substringBefore(' ')} in review" to text
}

/** A week without an import: what to tell the person. */
data class ImportReminder(val key: String, val title: String, val text: String)

/** More days than this without importing a statement brings a reminder, and then one a week while it stays so. */
const val IMPORT_REMINDER_DAYS = 7

/**
 * The day statements were last imported, in [zone]: the latest import into any account that takes statements, or,
 * for accounts imported into before imports were recorded, the latest transaction. Null when nothing ever was.
 */
fun lastImportDay(accounts: List<Account>, zone: ZoneId): LocalDate? {
    val importable = accounts.filter { it.format != StatementFormat.Manual }
    val imported = importable.mapNotNull { it.lastImportedAt }.maxOfOrNull { Instant.parse(it).atZone(zone).toLocalDate() }
    return imported ?: importable.mapNotNull { it.lastTransactionDate }.maxOfOrNull { isoDay(it) }
}

/**
 * The reminder to send today, if any: when more than [IMPORT_REMINDER_DAYS] days have gone by since the last import,
 * once, and again each further week, unless already told ([sent], by key). Nothing before the first import.
 */
fun importReminder(accounts: List<Account>, today: LocalDate, zone: ZoneId, sent: Map<String, Int>): ImportReminder? {
    val last = lastImportDay(accounts, zone) ?: return null
    val days = ChronoUnit.DAYS.between(last, today)
    if (days <= IMPORT_REMINDER_DAYS) return null
    // Day 8 is the first week's reminder, day 15 the second's; a new import starts again from its own day.
    val week = (days - 1) / IMPORT_REMINDER_DAYS
    val key = "import:$last:$week"
    if (key in sent) return null
    return ImportReminder(
        key = key,
        title = "No statements imported in $days days",
        text = "The last import was ${formatDay(last, year = false)}. " +
            "Import your latest statements to keep budgets and net worth up to date.",
    )
}
