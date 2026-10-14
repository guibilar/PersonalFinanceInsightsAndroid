package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.ReviewCategory
import com.guibilar.financeinsights.core.model.ReviewReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MonthlyReviewTest {
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
