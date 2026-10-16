package com.guibilar.financeinsights.core.calendar

import kotlinx.serialization.Serializable

// The models of the Calendar API of Hestia.Api (module Calendar, T103; contracts in
// Hestia.Modules.Calendar.Contracts/CalendarDtos.cs). Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601.

/**
 * One thing on the Calendar: an event of the Calendar module, or a dated thing of another module (a task due, a
 * birthday). A thing of whole days has [allDay]; else [startsAt] and [endsAt] say when.
 */
@Serializable
data class CalendarItem(
    /** Stable for the same thing and day; a repeated event adds the day of the occurrence. */
    val id: String,
    /** "Calendar", "Tasks", "People", "Documents", or "Home". */
    val module: String,
    /** "event", "task-due", "birthday", "reach-out", "loan-due", "document-expires", "warranty-ends". */
    val kind: String,
    val title: String,
    /** A second line (the place, "Turns 40"), or null. */
    val text: String? = null,
    val allDay: Boolean = true,
    val startDate: String,
    /** The last day (included). */
    val endDate: String = startDate,
    val startsAt: String? = null,
    val endsAt: String? = null,
    /** The web route of the thing, for example "/tasks/<id>"; the app maps it (todayDestination). */
    val link: String? = null,
    /** For an event of the Calendar module: its id. */
    val eventId: String? = null,
    val recurring: Boolean = false,
    /** For a task: false while it is open. */
    val done: Boolean? = null,
)

@Serializable
data class CalendarModuleStatus(val module: String, val status: String = "Complete")

/** The answer of `GET /api/calendar`: the items from [from] to [to], whole days first on each day, then by time. */
@Serializable
data class CalendarView(
    val today: String,
    val from: String,
    val to: String,
    val timeZone: String = "UTC",
    val items: List<CalendarItem> = emptyList(),
    val modules: List<CalendarModuleStatus> = emptyList(),
)

/** An event of the Calendar module. */
@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val allDay: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val timeZone: String = "UTC",
    val location: String? = null,
    val notes: String? = null,
    /** An iCalendar RRULE ("FREQ=WEEKLY;BYDAY=TU"), or null. */
    val repeat: String? = null,
    val skippedDates: List<String> = emptyList(),
    /** "Manual" or "Import". */
    val source: String = "Manual",
    val uid: String? = null,
)

/** A new event, or the new details of one (all values replace the old ones). */
@Serializable
data class CalendarEventRequest(
    val title: String,
    val allDay: Boolean,
    val startDate: String? = null,
    val endDate: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val timeZone: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val repeat: String? = null,
)

@Serializable
data class CalendarSkip(val date: String)

/** The text of an .ics file to import; [dryRun] only says what would change. */
@Serializable
data class CalendarImportRequest(val ics: String, val dryRun: Boolean)

/** One event of an imported file. [action]: "Add", "Update", "Same", "Delete", or "Skip". */
@Serializable
data class CalendarImportItem(
    val action: String,
    val title: String,
    val allDay: Boolean = false,
    val startDate: String? = null,
    val startsAt: String? = null,
    val repeat: String? = null,
    val eventId: String? = null,
    val reason: String? = null,
)

@Serializable
data class CalendarImportResult(
    val dryRun: Boolean = true,
    val added: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
    val events: List<CalendarImportItem> = emptyList(),
)
