package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.journal.JournalEntry
import java.time.LocalDate

/** Sample entries for the Journal tests and screenshots: a 6-day streak up to yesterday (Saturday, October 10, 2026). */
object JournalSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 10)

    private fun entry(day: LocalDate, text: String) = JournalEntry(
        id = "e-$day",
        day = day.toString(),
        text = text,
        createdAt = "${day}T20:00:00Z",
        updatedAt = "${day}T20:00:00Z",
    )

    val entries: List<JournalEntry> = listOf(
        entry(TODAY.minusDays(1), "Board game night with Marco. The new expansion is long but fun."),
        entry(TODAY.minusDays(2), "Rain all day. Read half of the new book."),
        entry(TODAY.minusDays(3), "Changed the furnace filter, finally."),
        entry(TODAY.minusDays(4), "Long call with Ana about the trip in May."),
        entry(TODAY.minusDays(5), "Quiet day."),
        entry(TODAY.minusDays(6), "Started the handouts for next week's session."),
        entry(TODAY.minusDays(9), "Before the gap."),
        entry(LocalDate.of(2025, 10, 10), "Moved the desk to the window. Much better light."),
    )
}
