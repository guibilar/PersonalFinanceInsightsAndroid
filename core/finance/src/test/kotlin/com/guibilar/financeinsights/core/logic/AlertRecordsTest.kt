package com.guibilar.financeinsights.core.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AlertRecordsTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun `forgets what no check can need again, and keeps the rest`() {
        val keys = setOf(
            "b1:2026-09", "b1:2026-10", "b2:2025", "b2:2026", "b3:all",
            "price:Netflix: Premium:2026-08-01", "price:Spotify:2026-09-20",
            "review:2026-08", "review:2026-09",
            "import:2026-09-01:1", "import:2026-09-20:1", "import:2026-09-20:2",
            "something-else",
        )
        assertEquals(
            setOf(
                // Budget warnings are server alerts since T71: all their records go.
                "b1:2026-09", "b1:2026-10", "b2:2025", "b2:2026", "b3:all", "review:2026-08",
                // Server alerts since T68: all their old records go.
                "price:Netflix: Premium:2026-08-01", "price:Spotify:2026-09-20",
                "import:2026-09-01:1", "import:2026-09-20:1", "import:2026-09-20:2",
            ),
            staleAlertKeys(keys, today),
        )
    }
}
