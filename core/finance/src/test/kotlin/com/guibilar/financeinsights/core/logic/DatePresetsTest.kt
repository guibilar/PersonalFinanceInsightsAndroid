package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.TransactionCriteria
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DatePresetsTest {
    private val today = LocalDate.of(2026, 9, 15)

    @Test
    fun `covers whole months before the running one, which stays open to today`() {
        val ranges = DatePreset.entries.associate { it.label to (it.range(today).startDate to it.range(today).endDate) }
        assertEquals(
            mapOf(
                "This month" to ("2026-09-01" to null),
                "Last month" to ("2026-08-01" to "2026-08-31"),
                "Last 3 months" to ("2026-06-01" to "2026-08-31"),
                "Last 6 months" to ("2026-03-01" to "2026-08-31"),
                "Last 12 months" to ("2025-09-01" to "2026-08-31"),
                "This year" to ("2026-01-01" to null),
                "Last year" to ("2025-01-01" to "2025-12-31"),
            ),
            ranges,
        )
    }

    @Test
    fun `last month in January is December of the year before`() {
        val range = DatePreset.LastMonth.range(LocalDate.of(2026, 1, 31))
        assertEquals("2025-12-01" to "2025-12-31", range.startDate to range.endDate)
    }

    @Test
    fun `matches criteria to a preset, all dates, or a custom range`() {
        assertEquals(DateChoice.All, matchDatePreset(TransactionCriteria(), today))
        assertEquals(
            DateChoice.Preset(DatePreset.Last3Months),
            matchDatePreset(TransactionCriteria(startDate = "2026-06-01", endDate = "2026-08-31"), today),
        )
        assertEquals(DateChoice.Custom, matchDatePreset(TransactionCriteria(startDate = "2026-06-02"), today))
    }

    @Test
    fun `describes a range in as few words as it takes`() {
        assertEquals("Sep 1 – today", describeRange(DatePreset.ThisMonth.range(today), today))
        assertEquals("Dec 1, 2025 – today", describeRange(DateRange(LocalDate.of(2025, 12, 1)), today))
        assertEquals("Jun 1 – Aug 31, 2026", describeRange(DatePreset.Last3Months.range(today)))
        assertEquals("Sep 1, 2025 – Aug 31, 2026", describeRange(DatePreset.Last12Months.range(today)))
        // One day (T65).
        assertEquals("Oct 3, 2026", describeRange(DateRange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3))))
    }
}
