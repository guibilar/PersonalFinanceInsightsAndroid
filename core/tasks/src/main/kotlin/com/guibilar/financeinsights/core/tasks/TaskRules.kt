package com.guibilar.financeinsights.core.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The rules of the Tasks screen, without Android: the sections, the short labels, the repeat form, and the new tasks
// that other screens make (the quick add, "Renew <document>"). The same rules as the web app (T83).

/** The days after today that "Upcoming" covers, as the server's `due=upcoming` and summary (Tasks:UpcomingDays). */
const val UPCOMING_DAYS = 7L

/** How many done tasks the "Done" section shows. */
const val DONE_SHOWN = 20

/** A "Renew" task for a document is due this many days before the document expires. */
const val RENEW_DAYS_BEFORE = 14L

/** The longest title that the server takes. */
const val MAX_TITLE = 200

/** The largest "every" of a repeat that the server takes. */
const val MAX_EVERY = 1000

/** A day from the API ("2026-10-03"); null for none or a value that is not a day. */
fun parseDay(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** The open tasks of the Tasks screen, in sections. The tasks in each section keep the server's order. */
data class TaskSections(
    /** Open and due before today. A repeated task too. */
    val overdue: List<TaskSummary> = emptyList(),
    /** Due today. A repeated task too. */
    val today: List<TaskSummary> = emptyList(),
    /** Not repeated, due in the next [UPCOMING_DAYS] days after today. */
    val upcoming: List<TaskSummary> = emptyList(),
    /** Not repeated, due later, or with no due date. */
    val later: List<TaskSummary> = emptyList(),
    /** All repeated tasks, the next due first: each shows its rule and next date. */
    val recurring: List<TaskSummary> = emptyList(),
) {
    val isEmpty get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && later.isEmpty() && recurring.isEmpty()
}

/**
 * Puts the open tasks into their sections. A repeated task that is due (today or before) shows in "Overdue" or
 * "Today", with a checkbox, and also in "Recurring". A repeated task that is not due yet shows only in "Recurring".
 */
fun taskSections(tasks: List<TaskSummary>, today: LocalDate): TaskSections {
    val open = tasks.filter { !it.isDone }
    val lastUpcoming = today.plusDays(UPCOMING_DAYS)
    fun due(task: TaskSummary) = parseDay(task.dueOn)
    return TaskSections(
        overdue = open.filter { task -> due(task)?.let { it < today } == true },
        today = open.filter { due(it) == today },
        upcoming = open.filter { task -> task.repeat == null && due(task)?.let { it > today && it <= lastUpcoming } == true },
        later = open.filter { task -> task.repeat == null && due(task).let { it == null || it > lastUpcoming } },
        recurring = open.filter { it.repeat != null }.sortedBy { due(it) ?: LocalDate.MAX },
    )
}

/** Only the open tasks due before today (the alert link "/tasks?due=overdue"). */
fun overdueTasks(tasks: List<TaskSummary>, today: LocalDate): List<TaskSummary> =
    tasks.filter { task -> !task.isDone && parseDay(task.dueOn)?.let { it < today } == true }

/** The last [max] done tasks, the latest first. By the moment, not the text: "10:00:00.5Z" is after "10:00:00Z". */
fun latestDone(tasks: List<TaskSummary>, max: Int = DONE_SHOWN): List<TaskSummary> =
    tasks.filter { it.isDone }.sortedByDescending { task ->
        (task.doneAt ?: task.updatedAt)?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: Instant.MIN
    }.take(max)

private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * A due date, short: "Today", "Tomorrow", "Yesterday", "3 days late", a weekday in the next week ("Tue"), else the
 * day ("Oct 20", with the year when it is not this year). Null for no due date.
 */
fun dueText(dueOn: String?, today: LocalDate): String? {
    val day = parseDay(dueOn) ?: return null
    val days = ChronoUnit.DAYS.between(today, day)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days < 0 -> "${-days} days late"
        days <= UPCOMING_DAYS -> weekday.format(day)
        day.year == today.year -> monthDay.format(day)
        else -> monthDayYear.format(day)
    }
}

/** A repeat in words: "Every day", "Every 4 days", "Every 12 months". */
fun repeatText(repeat: TaskRepeat): String =
    if (repeat.every == 1) "Every ${repeat.unit.one}" else "Every ${repeat.every} ${repeat.unit.many}"

/** How the next date is found, in plain words, as the web says it. */
fun repeatModeText(mode: RepeatMode): String = when (mode) {
    RepeatMode.FromCompletion -> "after each done"
    RepeatMode.FromDueDate -> "on a fixed schedule"
}

/** A repeat with its mode: "Every 4 days, after each done". */
fun repeatLongText(repeat: TaskRepeat): String = "${repeatText(repeat)}, ${repeatModeText(repeat.mode)}"

/** A line of the "Recurring" section: "Every 4 days · next Tue", or "Every week · Today" when it is due. */
fun recurringText(task: TaskSummary, today: LocalDate): String {
    val repeat = task.repeat?.let(::repeatText) ?: return dueText(task.dueOn, today).orEmpty()
    val due = parseDay(task.dueOn) ?: return repeat
    val text = dueText(task.dueOn, today)
    return if (due > today) "$repeat · next $text" else "$repeat · $text"
}

/** Tags as typed ("Home, garden ,home") as the server keeps them: lower case, trimmed, once each. */
fun parseTags(text: String): List<String> =
    text.split(',').map { it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

/** A task from the quick add: a title, and a due date (or none). Null when the title is empty. */
fun quickTask(title: String, dueOn: LocalDate?): NewTask? {
    val clean = title.trim()
    if (clean.isEmpty()) return null
    return NewTask(title = clean.take(MAX_TITLE), dueOn = dueOn?.toString())
}

/**
 * "Renew <title>" for a document with an expiry date: due [RENEW_DAYS_BEFORE] days before the expiry (today when that
 * day is past), linked to the document. Null without a valid expiry date. The same rule as the web.
 */
fun renewTask(documentId: String, documentTitle: String, expiresOn: String?, today: LocalDate): NewTask? {
    val expires = parseDay(expiresOn) ?: return null
    val due = expires.minusDays(RENEW_DAYS_BEFORE).let { if (it < today) today else it }
    val title = "Renew ${documentTitle.trim().ifEmpty { "the document" }}".take(MAX_TITLE)
    return NewTask(title = title, dueOn = due.toString(), links = listOf(NewTaskLink.document(documentId)))
}

/** The details of a task as typed in the task sheet. */
data class TaskDraft(
    val title: String = "",
    val notes: String = "",
    val dueOn: LocalDate? = null,
    /** As typed: "home, garden". */
    val tags: String = "",
    val repeats: Boolean = false,
    /** As typed, so a field can be empty for a moment. */
    val every: String = "1",
    val unit: RepeatUnit = RepeatUnit.Days,
    val mode: RepeatMode = RepeatMode.FromDueDate,
    /** Kept as it is: the app does not change the manual order. */
    val sortOrder: Int? = null,
) {
    /** Why the title can't be saved; null when it can. */
    val titleError: String?
        get() = when {
            title.isBlank() -> "Enter a title."
            title.trim().length > MAX_TITLE -> "A title can have at most $MAX_TITLE characters."
            else -> null
        }

    /** Why the repeat can't be saved; null when it can (or when the task does not repeat). */
    val everyError: String?
        get() = if (!repeats) null else when (every.trim().toIntOrNull()) {
            null -> "Enter a number."
            in 1..MAX_EVERY -> null
            else -> "Use 1 to $MAX_EVERY."
        }

    val canSave get() = titleError == null && everyError == null

    /** The repeat to send; null when the task does not repeat. */
    val repeat: TaskRepeat?
        get() = if (repeats) TaskRepeat(every.trim().toIntOrNull() ?: 1, unit, mode) else null

    /**
     * The details to send: all of them, empty ones as nothing. A repeated task without a due date becomes due today on
     * the server.
     */
    fun toDetails() = TaskDetails(
        title = title.trim(),
        notes = notes.trim().ifEmpty { null },
        dueOn = dueOn?.toString(),
        tags = parseTags(tags),
        repeat = repeat,
        sortOrder = sortOrder,
    )

    companion object {
        fun of(task: TaskItem) = TaskDraft(
            title = task.title,
            notes = task.notes.orEmpty(),
            dueOn = parseDay(task.dueOn),
            tags = task.tags.joinToString(", "),
            repeats = task.repeat != null,
            every = (task.repeat?.every ?: 1).toString(),
            unit = task.repeat?.unit ?: RepeatUnit.Days,
            mode = task.repeat?.mode ?: RepeatMode.FromDueDate,
            sortOrder = task.sortOrder,
        )
    }
}
