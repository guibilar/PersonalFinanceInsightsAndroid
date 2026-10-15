package com.guibilar.financeinsights.core.journal

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

// The rules of the Journal screen that need no Android: the labels of the days, the streak, and "On this day".

/** A day "yyyy-MM-dd", or null when the text is not a real day. */
fun journalDay(text: String?): LocalDate? = try {
    text?.takeIf { it.length == 10 }?.let(LocalDate::parse)
} catch (_: DateTimeParseException) {
    null
}

/** "Saturday, October 10", with the year when it is not the year of [today]. */
fun longDayText(day: LocalDate, today: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy", Locale.US))

/** "Oct 9, 2026". */
fun shortDayText(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))

/** The one-letter weekday of the week strip: "S", "M", "T", … */
fun weekdayLetter(day: LocalDate): String = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US)

/** "12-day streak", or null for none. */
fun streakText(streak: Int): String? = if (streak > 0) "$streak-day streak" else null

/** "Oct 10, 2025 · 1 year ago". */
fun onThisDayText(day: LocalDate, yearsAgo: Int): String =
    "${shortDayText(day)} · ${if (yearsAgo == 1) "1 year ago" else "$yearsAgo years ago"}"
