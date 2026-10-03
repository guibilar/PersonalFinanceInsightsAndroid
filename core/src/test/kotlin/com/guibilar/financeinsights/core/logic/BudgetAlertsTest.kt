package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetAlertsTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun budget(used: Double, period: BudgetPeriod = BudgetPeriod.Monthly, status: Status = Status.Active) = Budget(
        id = "b1", name = "Groceries", status = status, period = period, totalAmount = 150.0,
        totalSpent = 150 * used / 100, totalRemaining = 150 - 150 * used / 100, percentageUsed = used,
    )

    @Test
    fun `alerts once at 80 and once at 100, per month`() {
        val first = budgetAlerts(listOf(budget(84.0)), today, emptyMap()).single()
        assertEquals(80, first.level)
        assertEquals("Groceries is nearly used up", first.title)
        assertEquals("$126.00 of $150.00 this month (84%): $24.00 left.", first.text)

        assertTrue(budgetAlerts(listOf(budget(90.0)), today, mapOf(first.key to 80)).isEmpty())

        val over = budgetAlerts(listOf(budget(110.0)), today, mapOf(first.key to 80)).single()
        assertEquals(100, over.level)
        assertEquals("$165.00 of $150.00 this month: $15.00 over.", over.text)

        // Next month starts over.
        assertEquals(1, budgetAlerts(listOf(budget(84.0)), today.plusMonths(1), mapOf(first.key to 100)).size)
    }

    @Test
    fun `straight past 100 is one alert, and inactive or under 80 none`() {
        assertEquals(listOf(100), budgetAlerts(listOf(budget(120.0)), today, emptyMap()).map { it.level })
        assertTrue(budgetAlerts(listOf(budget(79.9)), today, emptyMap()).isEmpty())
        assertTrue(budgetAlerts(listOf(budget(120.0, status = Status.Inactive)), today, emptyMap()).isEmpty())
    }

    @Test
    fun `a total budget alerts once ever`() {
        val alert = budgetAlerts(listOf(budget(85.0, BudgetPeriod.Total)), today, emptyMap()).single()
        assertEquals("b1:all", alert.key)
        assertTrue(budgetAlerts(listOf(budget(85.0, BudgetPeriod.Total)), today.plusMonths(3), mapOf("b1:all" to 80)).isEmpty())
    }
}
