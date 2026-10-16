package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.calendar.CalendarEvent
import com.guibilar.financeinsights.core.calendar.CalendarImportItem
import com.guibilar.financeinsights.core.calendar.CalendarImportResult
import com.guibilar.financeinsights.core.calendar.CalendarItem
import java.time.LocalDate

/** Sample Calendar items, around Friday, October 16th, 2026 (times in UTC; the tests run in UTC). */
object CalendarSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 16)

    val dentist = CalendarItem(
        id = "calendar.event.ev1", module = "Calendar", kind = "event", title = "Dentist", text = "Main St 4",
        allDay = false, startDate = "2026-10-20", endDate = "2026-10-20",
        startsAt = "2026-10-20T15:00:00Z", endsAt = "2026-10-20T16:00:00Z", link = "/calendar?event=ev1", eventId = "ev1",
    )
    val filter = CalendarItem(
        id = "tasks.task-due.t1", module = "Tasks", kind = "task-due", title = "Change the furnace filter",
        startDate = "2026-10-20", link = "/tasks/t1", done = false,
    )
    val birthday = CalendarItem(
        id = "people.birthday.p1.2026", module = "People", kind = "birthday", title = "Ana's birthday", text = "Turns 36",
        startDate = "2026-10-21", link = "/people/p1",
    )
    val cabin = CalendarItem(
        id = "calendar.event.ev2", module = "Calendar", kind = "event", title = "Cabin weekend", text = "Canmore",
        startDate = "2026-10-23", endDate = "2026-10-25", link = "/calendar?event=ev2", eventId = "ev2",
    )
    val standup = CalendarItem(
        id = "calendar.event.ev3.20261019T0930", module = "Calendar", kind = "event", title = "Standup",
        allDay = false, startDate = "2026-10-19", startsAt = "2026-10-19T15:30:00Z", endsAt = "2026-10-19T15:45:00Z",
        link = "/calendar?event=ev3", eventId = "ev3", recurring = true,
    )

    /** In the server's order. */
    val items = listOf(standup, filter, dentist, birthday, cabin)

    val dentistEvent = CalendarEvent(
        id = "ev1", title = "Dentist", startsAt = "2026-10-20T15:00:00Z", endsAt = "2026-10-20T16:00:00Z",
        timeZone = "America/Edmonton", location = "Main St 4",
    )
    val standupEvent = CalendarEvent(
        id = "ev3", title = "Standup", startsAt = "2026-10-19T15:30:00Z", endsAt = "2026-10-19T15:45:00Z",
        timeZone = "America/Edmonton", repeat = "FREQ=WEEKLY;BYDAY=MO,WE,FR",
    )

    val importPreview = CalendarImportResult(
        dryRun = true, added = 1, unchanged = 1,
        events = listOf(
            CalendarImportItem("Add", "Yoga", startsAt = "2026-10-21T00:00:00Z", repeat = "FREQ=WEEKLY"),
            CalendarImportItem("Same", "Long weekend", allDay = true, startDate = "2026-10-30", eventId = "ev9"),
        ),
    )
}
