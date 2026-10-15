package com.guibilar.financeinsights.timeline

import java.io.IOException
import java.time.LocalDate

/** The Timeline API on fixed entries: today is Saturday, October 10, 2026. Each call is recorded; any can fail. */
class FakeTimelineApi(var entries: List<TimelineEntry> = samples) : TimelineApi {
    val sent = mutableListOf<Triple<String?, Int, String?>>()
    var failing = false
    var failedModules = emptyList<String>()

    override suspend fun page(before: String?, days: Int, module: String?): TimelinePage {
        sent += Triple(before, days, module)
        if (failing) throw IOException("offline")
        val to = before?.let { LocalDate.parse(it).minusDays(1) } ?: TODAY
        val from = to.minusDays(days - 1L)
        val ofModule = entries.filter { module == null || it.module == module }
        val shown = ofModule.filter { LocalDate.parse(it.day) in from..to }
        // As the host: the next page ends on the latest earlier day with an entry; none: nothing earlier.
        val latest = ofModule.map { LocalDate.parse(it.day) }.filter { it < from }.maxOrNull()
        return TimelinePage(
            today = TODAY.toString(),
            from = from.toString(),
            to = to.toString(),
            earlier = (latest?.plusDays(1) ?: from).toString(),
            hasEarlier = latest != null,
            days = shown.groupBy { it.day }.toSortedMap(compareByDescending { it }).map { (day, list) -> TimelineDay(day, list) },
            modules = listOf("Documents", "Home", "Journal", "Money", "People", "Tasks")
                .map { TimelineModule(it, if (it in failedModules) "Failed" else "Complete") },
        )
    }

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 10)

        val samples = listOf(
            TimelineEntry("money.transaction.t1", "Money", "transaction", "2026-10-10", null, "Costco", "Groceries · Neo card", -142.18, "/money/transactions?id=t1"),
            TimelineEntry("journal.entry.e1", "Journal", "entry", "2026-10-10", null, "Journal", "Session prep for Saturday's game. Finished the Ashport handouts.", null, "/journal?day=2026-10-10"),
            TimelineEntry("documents.document-added.d1", "Documents", "document-added", "2026-10-09", "2026-10-09T20:15:00Z", "Car insurance policy 2026", "Insurance", null, "/documents/d1"),
            TimelineEntry("tasks.task-done.c1", "Tasks", "task-done", "2026-10-09", "2026-10-09T15:40:00Z", "Changed the furnace filter", "Done · repeats", null, "/tasks/k1"),
            TimelineEntry("money.month-review.2026-09", "Money", "month-review", "2026-10-01", null, "September in review", "41 purchases, 6 days without spending", -1234.5, "/money/review?year=2026&month=9"),
            TimelineEntry("money.transaction.t2", "Money", "transaction", "2026-09-25", null, "Payroll", "Salary · CIBC chequing", 2400.0, "/money/transactions?id=t2"),
        )
    }
}
