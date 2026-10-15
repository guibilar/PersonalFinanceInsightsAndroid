package com.guibilar.financeinsights.core.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class JournalRulesTest {
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `days and their labels`() {
        assertEquals(LocalDate.of(2026, 10, 8), journalDay("2026-10-08"))
        assertNull(journalDay("2026-02-30"))
        assertNull(journalDay("yesterday"))
        assertNull(journalDay(null))

        assertEquals("Saturday, October 10", longDayText(today, today))
        assertEquals("Friday, October 10, 2025", longDayText(LocalDate.of(2025, 10, 10), today))
        assertEquals("Oct 9, 2026", shortDayText(today.minusDays(1)))
        assertEquals("S", weekdayLetter(today))
        assertEquals("F", weekdayLetter(today.minusDays(1)))
    }

    @Test
    fun `the streak and on this day`() {
        assertEquals("12-day streak", streakText(12))
        assertNull(streakText(0))
        assertEquals("Oct 10, 2025 · 1 year ago", onThisDayText(LocalDate.of(2025, 10, 10), 1))
        assertEquals("Oct 10, 2023 · 3 years ago", onThisDayText(LocalDate.of(2023, 10, 10), 3))
    }
}
