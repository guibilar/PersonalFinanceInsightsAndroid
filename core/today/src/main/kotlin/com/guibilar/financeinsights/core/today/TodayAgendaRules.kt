package com.guibilar.financeinsights.core.today

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The rules of "Your day" and "On this day" on Today (T106), the same as the web's.

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** "9:00 AM" in [zone]; null when the text is not a moment. */
fun agendaTime(moment: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
    moment?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(zone).format(TIME) }.getOrNull() }

/** "3 things today, and a birthday this week." (or "Here is how things are at home today." without an agenda). */
fun daySummary(agenda: TodayAgenda?): String {
    if (agenda == null) return "Here is how things are at home today."
    val count = agenda.today.size
    val today = when (count) {
        0 -> "Nothing planned today"
        1 -> "1 thing today"
        else -> "$count things today"
    }
    val birthday = if (agenda.upcoming.any { it.kind == "birthday" }) ", and a birthday this week" else ""
    return "$today$birthday."
}

/** The second line of an item: the place or the detail, "until Sun, Oct 25", "Task", "until 8:30 PM". */
fun agendaMeta(item: TodayAgendaItem, zone: ZoneId = ZoneId.systemDefault()): String = buildList {
    item.text?.let { add(it) }
    if (item.endDate != item.startDate) runCatching { LocalDate.parse(item.endDate).format(SHORT_DAY) }.getOrNull()?.let { add("until $it") }
    if (item.kind == "task-due") add("Task")
    if (!item.allDay && item.endsAt != null && item.endsAt != item.startsAt) agendaTime(item.endsAt, zone)?.let { add("until $it") }
}.joinToString(" · ")

/** "Tomorrow" or "Tue", and the time for one with a time: "Tue 9:00 AM". */
fun upcomingDay(item: TodayAgendaItem, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = runCatching { LocalDate.parse(item.startDate) }.getOrNull()
    val name = when (day) {
        null -> ""
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(WEEKDAY)
    }
    val time = if (!item.allDay) agendaTime(item.startsAt, zone) else null
    return listOfNotNull(name.ifEmpty { null }, time).joinToString(" ")
}

/** "1 year ago · Oct 16, 2025". */
fun memoryWhen(memory: TodayMemory): String {
    val ago = if (memory.yearsAgo == 1) "1 year ago" else "${memory.yearsAgo} years ago"
    val day = runCatching { LocalDate.parse(memory.day).format(DAY) }.getOrDefault(memory.day)
    return "$ago · $day"
}

/** The task id of a "task-due" item ("/tasks/<id>"); null for another item. */
fun taskIdOf(item: TodayAgendaItem): String? =
    if (item.kind == "task-due") item.link?.removePrefix("/tasks/")?.substringBefore('?')?.takeIf { it.isNotBlank() && '/' !in it } else null
