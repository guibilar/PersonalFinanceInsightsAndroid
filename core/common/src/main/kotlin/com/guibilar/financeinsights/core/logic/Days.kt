package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.isoDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** A day as "May 31, 2026", or "May 31" without the year. */
fun formatDay(day: LocalDate, year: Boolean = true): String = (if (year) monthDayYear else monthDay).format(day)

/** The same for a day from the API ("2026-05-31T00:00:00Z", "2026-05-31"). */
fun formatDay(iso: String, year: Boolean = true): String = formatDay(isoDay(iso), year)
