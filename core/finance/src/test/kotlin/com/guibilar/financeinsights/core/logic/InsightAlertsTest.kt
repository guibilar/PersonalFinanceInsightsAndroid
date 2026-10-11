package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.PriceChange
import com.guibilar.financeinsights.core.model.RecurringCadence
import com.guibilar.financeinsights.core.model.RecurringPayment
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.RecurringStatus
import com.guibilar.financeinsights.core.model.ReviewCategory
import com.guibilar.financeinsights.core.model.ReviewReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightAlertsTest {
    private fun payment(name: String, change: PriceChange?, status: RecurringStatus = RecurringStatus.Active) = RecurringPayment(
        name = name, cadence = RecurringCadence.Monthly, status = status, amount = change?.to ?: 10.0,
        firstDate = "2026-01-05T00:00:00Z", lastDate = "2026-10-05T00:00:00Z", lastAmount = change?.to ?: 10.0,
        nextDate = "2026-11-05T00:00:00Z", count = 10, monthlyCost = 10.0, yearlyCost = 120.0, priceChange = change,
    )

    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `a price that just went up is told once, with what it costs a year`() {
        val report = RecurringReport(listOf(payment("NETFLIX", PriceChange(16.49, 18.99, "2026-10-05T00:00:00Z")), payment("SPOTIFY", null)))

        val alert = priceAlerts(report, today, emptyMap()).single()

        assertEquals("NETFLIX went up", alert.title)
        assertEquals("Now $18.99 every month, from $16.49: $30.00 more a year.", alert.text)
        assertTrue(priceAlerts(report, today, mapOf(alert.key to 1)).isEmpty())
    }

    @Test
    fun `an old change, or one that stopped, isn't news`() {
        val old = payment("GYM", PriceChange(40.0, 50.0, "2026-08-01T00:00:00Z"))
        val stopped = payment("PHONE", PriceChange(40.0, 30.0, "2026-10-01T00:00:00Z"), RecurringStatus.Stopped)

        assertTrue(priceAlerts(RecurringReport(listOf(old, stopped)), today, emptyMap()).isEmpty())
    }

    @Test
    fun `the review is offered in the first week of a month, for the month before`() {
        assertEquals("review:2026-09", reviewKey(LocalDate.of(2026, 10, 1)))
        assertEquals("review:2025-12", reviewKey(LocalDate.of(2026, 1, 7)))
        assertNull(reviewKey(LocalDate.of(2026, 10, 8)))
    }

    @Test
    fun `the review says what was spent, against the month before, and where most went`() {
        val review = ReviewReport(
            label = "September 2026", year = 2026, month = 9, start = "2026-09-01", end = "2026-09-30", complete = true,
            previousLabel = "August", spending = 1100.0, previousSpending = 1000.0, income = 0.0, previousIncome = 0.0,
            purchases = 4, noSpendDays = 26,
            categories = listOf(ReviewCategory(CategoryReference("c", "Rent", "#000000"), 900.0, 900.0)),
        )

        assertEquals(
            "Your September in review" to "$1,100.00 spent, 10% more than August. Most went to Rent, $900.00.",
            reviewAlert(review),
        )
    }
}
