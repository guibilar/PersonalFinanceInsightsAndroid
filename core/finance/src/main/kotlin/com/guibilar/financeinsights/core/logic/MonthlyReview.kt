package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.ReviewReport
import java.time.LocalDate
import java.time.YearMonth

// The monthly review notification: the one local notification rule with no server alert (T68). The price changes and
// the import reminders are server alerts now ("price-changed" and "statements-not-imported").

/** The monthly review is offered in the first week of the month, once. */
private const val REVIEW_DAYS = 7

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
