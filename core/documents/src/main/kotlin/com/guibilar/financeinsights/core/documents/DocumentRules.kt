package com.guibilar.financeinsights.core.documents

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How many days ahead the list's "Expire soon" section looks (the same window as the server's Today items). */
const val EXPIRE_SOON_DAYS = 30L

/** The "Expire soon" section: from today to [EXPIRE_SOON_DAYS] days later, both included, as the API's filter. */
fun expireSoonRange(today: LocalDate): Pair<String, String> = today.toString() to today.plusDays(EXPIRE_SOON_DAYS).toString()

/** Days from [today] to [expiresOn] ("yyyy-MM-dd"): 0 today, negative when it expired; null without a valid day. */
fun daysUntil(expiresOn: String?, today: LocalDate): Long? =
    expiresOn?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.let { ChronoUnit.DAYS.between(today, it) }

/** A short label for the days left: "Expired", "Today", "Tomorrow", "22 days". */
fun expiryLabel(days: Long): String = when {
    days < 0 -> "Expired"
    days == 0L -> "Today"
    days == 1L -> "Tomorrow"
    else -> "$days days"
}

/** The longest side of a photo after the app scales it for the upload. */
const val MAX_IMAGE_SIDE = 2500

/**
 * The size of an image of [width] × [height] after the scale for the upload: the long side at most [maxSide],
 * the same shape. A smaller image keeps its size.
 */
fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_IMAGE_SIDE): Pair<Int, Int> {
    val longSide = maxOf(width, height)
    if (longSide <= maxSide || longSide <= 0) return width to height
    val scale = maxSide.toDouble() / longSide
    return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
}
