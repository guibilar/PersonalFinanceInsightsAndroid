package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.journal.JournalApi
import com.guibilar.financeinsights.core.journal.JournalDay
import com.guibilar.financeinsights.core.journal.JournalEntry
import com.guibilar.financeinsights.core.journal.JournalEntrySummary
import com.guibilar.financeinsights.core.journal.JournalPage
import com.guibilar.financeinsights.core.journal.JournalSummary
import com.guibilar.financeinsights.core.journal.JournalText
import com.guibilar.financeinsights.core.journal.OnThisDay
import java.io.IOException
import java.time.LocalDate

/**
 * The Journal API on entries in memory, as the server keeps them: one per day, the streak and "On this day" computed,
 * saves and deletes kept. "Today" is [today]. Each call is recorded, and any can be made to fail.
 */
class FakeJournalApi(
    entries: List<JournalEntry> = JournalSamples.entries,
    var today: LocalDate = JournalSamples.TODAY,
) : JournalApi {
    val entries = entries.toMutableList()

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

    private fun notFound(day: String) = ApiError.Http(404, "Not found", "Journal entry of $day was not found.", null, emptyMap())

    private fun summaryOf(entry: JournalEntry): JournalEntrySummary {
        val line = entry.text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        val excerpt = if (line.length <= 200) line else line.take(199).trimEnd() + "…"
        return JournalEntrySummary(entry.id, entry.day, excerpt, entry.text.length, entry.updatedAt)
    }

    override suspend fun summary(): JournalSummary = call("summary", null) {
        val written = entries.map { LocalDate.parse(it.day) }.toSet()
        var day = if (today in written) today else today.minusDays(1)
        var streak = 0
        while (day in written) {
            streak++
            day = day.minusDays(1)
        }
        JournalSummary(
            date = today.toString(),
            streak = streak,
            week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { JournalDay(it.toString(), it in written) },
            today = entries.firstOrNull { it.day == today.toString() },
            onThisDay = entries.map { it to LocalDate.parse(it.day) }
                .filter { (_, d) -> d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth }
                .sortedByDescending { (_, d) -> d }
                .map { (e, d) -> OnThisDay(today.year - d.year, summaryOf(e)) },
            entryCount = entries.size,
        )
    }

    override suspend fun entries(q: String?, from: String?, to: String?, page: Int, pageSize: Int): JournalPage =
        call("entries", listOf(q, from, to, page, pageSize)) {
            val words = q?.lowercase()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
            val found = entries
                .filter { e -> from == null || e.day >= from }
                .filter { e -> to == null || e.day <= to }
                .filter { e -> words.all { it in e.text.lowercase() } }
                .sortedByDescending { it.day }
            val items = found.drop((page - 1) * pageSize).take(pageSize).map(::summaryOf)
            JournalPage(items, page, pageSize, found.size, page * pageSize < found.size)
        }

    override suspend fun entry(day: String): JournalEntry = call("entry", day) {
        entries.firstOrNull { it.day == day } ?: throw notFound(day)
    }

    override suspend fun save(day: String, text: JournalText): JournalEntry = call("save", day to text.text) {
        val trimmed = text.text.trim()
        if (trimmed.isEmpty()) throw ApiError.Http(400, "Invalid input", "Write something, or delete the entry.", null, emptyMap())
        if (LocalDate.parse(day) > today) throw ApiError.Http(400, "Invalid input", "An entry cannot be for a day after today.", null, emptyMap())
        val existing = entries.firstOrNull { it.day == day }
        val saved = existing?.copy(text = trimmed, updatedAt = "${today}T21:00:00Z")
            ?: JournalEntry("e-$day", day, trimmed, "${today}T21:00:00Z", "${today}T21:00:00Z")
        entries.removeAll { it.day == day }
        entries += saved
        saved
    }

    override suspend fun delete(day: String) = call("delete", day) {
        if (!entries.removeAll { it.day == day }) throw notFound(day)
    }
}
