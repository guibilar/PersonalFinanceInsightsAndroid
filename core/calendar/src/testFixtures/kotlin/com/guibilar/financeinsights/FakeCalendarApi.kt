package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.calendar.CalendarApi
import com.guibilar.financeinsights.core.calendar.CalendarEvent
import com.guibilar.financeinsights.core.calendar.CalendarEventRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportResult
import com.guibilar.financeinsights.core.calendar.CalendarItem
import com.guibilar.financeinsights.core.calendar.CalendarModuleStatus
import com.guibilar.financeinsights.core.calendar.CalendarSkip
import com.guibilar.financeinsights.core.calendar.CalendarView
import java.io.IOException
import java.time.LocalDate

/**
 * The Calendar API on items in memory: [range] gives the items that touch the days asked. Each call is recorded, and any
 * can be made to fail.
 */
class FakeCalendarApi(
    var items: List<CalendarItem> = CalendarSamples.items,
    var today: LocalDate = CalendarSamples.TODAY,
    val events: MutableMap<String, CalendarEvent> = mutableMapOf(
        "ev1" to CalendarSamples.dentistEvent,
        "ev3" to CalendarSamples.standupEvent,
    ),
    var importAnswer: CalendarImportResult = CalendarSamples.importPreview,
    var modules: List<CalendarModuleStatus> = listOf("Calendar", "Documents", "Home", "People", "Tasks").map { CalendarModuleStatus(it) },
) : CalendarApi {
    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()

    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    override suspend fun range(from: String, to: String): CalendarView = call("range", from to to) {
        CalendarView(today.toString(), from, to, "UTC", items.filter { it.startDate <= to && it.endDate >= from }, modules)
    }

    override suspend fun event(id: String): CalendarEvent = call("event", id) {
        events[id] ?: throw ApiError.Http(404, "Not found", null, null, emptyMap())
    }

    override suspend fun create(event: CalendarEventRequest): CalendarEvent = call("create", event) {
        CalendarEvent("new", event.title, event.allDay, event.startDate, event.endDate, event.startsAt, event.endsAt, repeat = event.repeat)
            .also { events[it.id] = it }
    }

    override suspend fun update(id: String, event: CalendarEventRequest): CalendarEvent = call("update", id to event) {
        CalendarEvent(id, event.title, event.allDay, event.startDate, event.endDate, event.startsAt, event.endsAt, repeat = event.repeat)
            .also { events[id] = it }
    }

    override suspend fun skip(id: String, skip: CalendarSkip): CalendarEvent = call("skip", id to skip.date) {
        val event = events.getValue(id)
        event.copy(skippedDates = event.skippedDates + skip.date).also { events[id] = it }
    }

    override suspend fun delete(id: String) = call("delete", id) { events.remove(id); Unit }

    override suspend fun import(request: CalendarImportRequest): CalendarImportResult = call("import", request) {
        importAnswer.copy(dryRun = request.dryRun)
    }
}
