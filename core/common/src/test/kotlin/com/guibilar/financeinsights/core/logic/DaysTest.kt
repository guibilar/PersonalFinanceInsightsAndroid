package com.guibilar.financeinsights.core.logic

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DaysTest {
    @Test
    fun `a day reads with its year, or without`() {
        assertEquals("May 31, 2026", formatDay(LocalDate.of(2026, 5, 31)))
        assertEquals("May 31", formatDay(LocalDate.of(2026, 5, 31), year = false))
    }

    @Test
    fun `an API day reads as that day, whatever its time`() {
        assertEquals("Oct 20", formatDay("2026-10-20T00:00:00Z", year = false))
        assertEquals("Oct 20, 2026", formatDay("2026-10-20"))
    }
}
