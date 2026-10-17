package com.guibilar.financeinsights.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// The rules of the Calendar screen (T104), the same as the web's (features/calendar/calendar.ts).

/** A day from "yyyy-MM-dd"; null when the text is not one. */
fun calendarDay(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** A moment from ISO 8601; null when the text is not one. */
fun moment(text: String?): OffsetDateTime? = text?.let {
    try {
        OffsetDateTime.parse(it)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** The weeks of a month: whole weeks from the Sunday on or before the 1st to the Saturday on or after the last day. */
fun monthWeeks(month: YearMonth): List<List<LocalDate>> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    return generateSequence(start) { it.plusWeeks(1) }.takeWhile { it <= end }
        .map { week -> (0L until 7L).map { week.plusDays(it) } }.toList()
}

/** The items that touch [day], in the server's order (whole days first, then by time). */
fun itemsOn(items: List<CalendarItem>, day: LocalDate): List<CalendarItem> {
    val text = day.toString()
    return items.filter { it.startDate <= text && it.endDate >= text }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val LONG_DAY_YEAR = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/** "9:00 AM" in [zone] (the phone's). */
fun timeText(moment: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
    moment(moment)?.atZoneSameInstant(zone)?.format(TIME)

/** "Mon, Oct 20". */
fun shortDayText(day: LocalDate): String = day.format(SHORT_DAY)

/** "October 2026". */
fun monthText(month: YearMonth): String = month.format(MONTH)

/** "Today", "Tomorrow", or "Tuesday, October 20" (with the year when it is not this year). */
fun dayHeading(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> day.format(if (day.year == today.year) LONG_DAY else LONG_DAY_YEAR)
}

/** "All day", "All day · until Sun, Oct 25", "9:00 AM", or "9:00 AM – 10:30 AM". */
fun whenText(item: CalendarItem, zone: ZoneId = ZoneId.systemDefault()): String {
    if (item.allDay || item.startsAt == null) {
        val end = calendarDay(item.endDate)
        return if (item.endDate == item.startDate || end == null) "All day" else "All day · until ${shortDayText(end)}"
    }
    val start = timeText(item.startsAt, zone) ?: return "All day"
    val end = item.endsAt?.takeIf { it != item.startsAt }?.let { timeText(it, zone) }
    return if (end == null) start else "$start – $end"
}

/** The accent of an item's module ("calendar", "tasks", …), as on the web (data-accent). */
fun accentId(item: CalendarItem): String = item.module.lowercase(Locale.ROOT)

/** What the item is, in one word for the row: "Event", "Tasks", … */
fun moduleLabel(item: CalendarItem): String = if (item.module == "Calendar") "Event" else item.module

/** A repeat that the event sheet offers. [rule] is the RRULE without UNTIL. */
data class RepeatPreset(val label: String, val rule: String)

val REPEAT_PRESETS = listOf(
    RepeatPreset("Does not repeat", ""),
    RepeatPreset("Every day", "FREQ=DAILY"),
    RepeatPreset("Every week", "FREQ=WEEKLY"),
    RepeatPreset("Every 2 weeks", "FREQ=WEEKLY;INTERVAL=2"),
    RepeatPreset("Every month", "FREQ=MONTHLY"),
    RepeatPreset("Every year", "FREQ=YEARLY"),
)

private val UNTIL_MOMENT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

/** The day of an UNTIL in [zone]: a day ("20261231") as it is, a UTC moment ("20270101T045959Z") on the phone's day. */
private fun untilDay(value: String, zone: ZoneId): LocalDate? =
    runCatching { LocalDateTime.parse(value, UNTIL_MOMENT).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

/** The RRULE without its UNTIL, and the UNTIL day in [zone], if any. */
fun splitRepeat(repeat: String?, zone: ZoneId = ZoneId.systemDefault()): Pair<String, LocalDate?> {
    if (repeat.isNullOrBlank()) return "" to null
    var until: LocalDate? = null
    val parts = repeat.split(';').filter { part ->
        val key = part.substringBefore('=').uppercase(Locale.ROOT)
        if (key == "UNTIL") {
            until = untilDay(part.substringAfter('='), zone)
            false
        } else {
            true
        }
    }
    return parts.joinToString(";") to until
}

/**
 * The RRULE of a preset and an end day: an all-day event ends on the day; one with a time at the end of the day in
 * [zone], written in UTC as RFC 5545 asks. The end of the UTC day would drop the last evening in the Americas (7 PM in
 * Toronto is after midnight UTC).
 */
fun joinRepeat(rule: String, until: LocalDate?, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (rule.isBlank()) return null
    if (until == null) return rule
    if (allDay) return "$rule;UNTIL=${until.format(DateTimeFormatter.BASIC_ISO_DATE)}"
    val end = until.atTime(LocalTime.of(23, 59, 59)).atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
    return "$rule;UNTIL=${end.format(UNTIL_MOMENT)}"
}

/** "Every week", "Every week until Thu, Dec 31", or "Repeats (custom)"; null for no repeat. */
fun repeatText(repeat: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (repeat.isNullOrBlank()) return null
    val (rule, until) = splitRepeat(repeat, zone)
    val base = REPEAT_PRESETS.firstOrNull { it.rule == rule && it.rule.isNotEmpty() }?.label ?: "Repeats (custom)"
    return if (until != null) "$base until ${shortDayText(until)}" else base
}

/** The moment of a local day and time in [zone], as ISO 8601 (what the API takes). */
fun isoMoment(day: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): String =
    day.atTime(time).atZone(zone).toOffsetDateTime().toString()

/** "2 events added, 1 changed." */
fun importSummary(result: CalendarImportResult): String {
    val parts = buildList {
        if (result.added > 0) add("${result.added} event${if (result.added == 1) "" else "s"} added")
        if (result.updated > 0) add("${result.updated} changed")
        if (result.deleted > 0) add("${result.deleted} cancelled")
        if (result.unchanged > 0 && isEmpty()) add("${result.unchanged} already in the calendar")
        if (result.skipped > 0) add("${result.skipped} skipped")
    }
    return if (parts.isEmpty()) "The file has no events." else parts.joinToString(", ") + "."
}

/** The events that an import adds, changes, or deletes. */
fun importChanges(result: CalendarImportResult): Int = result.added + result.updated + result.deleted
