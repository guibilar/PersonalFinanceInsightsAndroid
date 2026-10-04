package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.ReviewReport
import java.time.LocalDate
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
