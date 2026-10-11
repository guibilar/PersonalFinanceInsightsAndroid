package com.guibilar.financeinsights.core.logic

import org.junit.After
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyPrivacyTest {
    @After
    fun showAgain() {
        Money.hidden = { false }
    }

    @Test
    fun `hides every amount at the same length in privacy mode`() {
        Money.hidden = { true }

        assertEquals("$•••••", Money.format(1234.56))
        assertEquals("$•••••", Money.format(5.0))
        assertEquals("-$•••••", Money.format(-5.0))
        assertEquals("$•••••", Money.format(-0.001))
        assertEquals("", Money.compact(1200.0))
        // Percentages say nothing of the amounts: kept.
        assertEquals("84%", Money.percentUsed(84.4))
    }

    @Test
    fun `hides the amounts in notifications too`() {
        Money.hidden = { true }
        val budget = Budget(
            id = "b1", name = "Groceries", status = Status.Active, period = BudgetPeriod.Monthly, totalAmount = 150.0,
            totalSpent = 165.0, totalRemaining = -15.0, percentageUsed = 110.0,
        )

        val alert = budgetAlerts(listOf(budget), LocalDate.of(2026, 10, 9), emptyMap()).single()

        assertTrue(alert.text, alert.text.contains("$••••• of $•••••"))
        assertFalse(alert.text, alert.text.contains(Regex("""\$\d""")))
    }
}
