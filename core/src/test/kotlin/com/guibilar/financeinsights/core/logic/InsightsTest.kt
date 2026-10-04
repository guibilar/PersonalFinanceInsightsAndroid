package com.guibilar.financeinsights.core.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    @Test
    fun `a change reads as people say it`() {
        assertEquals("10% more than August", Change.of(110.0, 100.0)!!.than("August"))
        assertEquals("20% less than usual", Change.of(80.0, 100.0)!!.than("usual"))
        assertEquals("about the same as usual", Change.of(100.5, 100.0)!!.than("usual"))
        assertNull(Change.of(50.0, 0.0))
    }

    @Test
    fun `an API day reads as that day`() {
        assertEquals("Oct 20", shortDay("2026-10-20T00:00:00Z"))
    }

    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun `a review steps a month at a time, across years, up to the running month`() {
        assertEquals(ReviewPeriod(2025, 12), ReviewPeriod(2026, 1).step(-1, today, "2025-03"))
        assertEquals(ReviewPeriod(2026, 10), ReviewPeriod(2026, 9).step(1, today, null))
        assertNull(ReviewPeriod(2026, 10).step(1, today, null))
        assertNull(ReviewPeriod(2025, 3).step(-1, today, "2025-03"))
    }

    @Test
    fun `a year review steps a year at a time, a year with any data being there`() {
        assertEquals(ReviewPeriod(2025, null), ReviewPeriod(2026, null).step(-1, today, "2025-11"))
        assertNull(ReviewPeriod(2025, null).step(-1, today, "2025-11"))
        assertNull(ReviewPeriod(2026, null).step(1, today, null))
    }
}
