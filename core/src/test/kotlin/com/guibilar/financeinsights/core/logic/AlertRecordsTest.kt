package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Status
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
            setOf("b1:2026-09", "b2:2025", "price:Netflix: Premium:2026-08-01", "review:2026-08", "import:2026-09-01:1"),
            staleAlertKeys(keys, today),
        )
    }

    @Test
    fun `a budget back under a level it alerted at can alert again`() {
        fun budget(used: Double) = Budget(
            id = "b1", name = "Trip", status = Status.Active, period = BudgetPeriod.Custom, totalAmount = 150.0,
            totalSpent = 150 * used / 100, totalRemaining = 150 - 150 * used / 100, percentageUsed = used,
        )
        val sent = mapOf("b1:all" to 100)

        assertEquals(mapOf("b1:all" to 80), budgetLevelsDropped(listOf(budget(85.0)), today, sent))
        assertEquals(mapOf("b1:all" to 0), budgetLevelsDropped(listOf(budget(40.0)), today, sent))
        assertEquals(emptyMap<String, Int>(), budgetLevelsDropped(listOf(budget(120.0)), today, sent))
        // Forgotten, the next crossing is told.
        assertEquals(1, budgetAlerts(listOf(budget(120.0)), today, mapOf("b1:all" to 0)).size)
    }
}
