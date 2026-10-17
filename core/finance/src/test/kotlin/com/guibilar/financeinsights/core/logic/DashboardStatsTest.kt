package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.CategorizedReport
import com.guibilar.financeinsights.core.model.MonthlyCategoryValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DashboardStatsTest {
    private fun report(vararg months: Pair<String, Map<String, Double>>, previous: Double? = null) = CategorizedReport(
        monthlyData = months.map { (month, values) -> MonthlyCategoryValues(month, values.values.sum(), values) },
        previousMonthToDateTotal = previous,
    )

    @Test
    fun `reads report labels as months`() {
        assertEquals(YearMonth.of(2026, 9), monthOf("Sep 2026"))
        assertNull(monthOf("September 2026"))
    }

    @Test
    fun `compares this month with last month up to the same day`() {
        assertEquals("Aug 1–5", previousMonthToDateLabel(LocalDate.of(2026, 9, 5)))
        assertEquals("Feb 1–28", previousMonthToDateLabel(LocalDate.of(2026, 3, 30)))
        assertEquals("Aug 1", previousMonthToDateLabel(LocalDate.of(2026, 9, 1)))
        val hint = spentHint(report("Sep 2026" to mapOf("Dining" to 120.0), previous = 100.0), LocalDate.of(2026, 9, 5))!!
        assertEquals(SpentHint("20% more than Aug 1–5", up = true, worse = true), hint)
        assertNull(spentHint(report("Sep 2026" to mapOf("Dining" to 120.0)), LocalDate.of(2026, 9, 5)))
        // Within 1% is about the same, as in the Insights, not "0% more".
        assertEquals(
            SpentHint("about the same as Aug 1–5", up = false, worse = false),
            spentHint(report("Sep 2026" to mapOf("Dining" to 100.4), previous = 100.0), LocalDate.of(2026, 9, 5)),
        )
    }

    @Test
    fun `averages the finished months, or every month while none is finished`() {
        val labels = listOf("Jul 2026", "Aug 2026", "Sep 2026")
        assertEquals(listOf(0, 1), finishedMonths(labels, LocalDate.of(2026, 9, 15)))
        assertEquals(listOf(0), finishedMonths(listOf("Jan 2026"), LocalDate.of(2026, 1, 15)))
        assertEquals("Over 2 finished months", averageCaption(labels, LocalDate.of(2026, 9, 15)))
        assertEquals("This month so far", averageCaption(listOf("Jan 2026"), LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun `shares leave out a category that was mostly returned, so they add up to 100%`() {
        val shares = categoryShares(report("Sep 2026" to mapOf("Dining" to 100.0, "Shopping" to -50.0, "Groceries" to 300.0)))
        assertEquals(listOf("Groceries" to 75.0, "Dining" to 25.0), shares.map { it.category to it.percent })
    }

    @Test
    fun `averages each category over the finished months, next to this month so far`() {
        val year = report(
            "Jul 2026" to mapOf("Dining" to 100.0, "Groceries" to 300.0),
            "Aug 2026" to mapOf("Groceries" to 500.0),
            "Sep 2026" to mapOf("Dining" to 80.0, "Pet" to 20.0),
        )
        val averages = categoryAverages(year, LocalDate.of(2026, 9, 15))
        assertEquals(
            listOf(
                CategoryAverage("Groceries", 400.0, 0.0, -100.0),
                CategoryAverage("Dining", 50.0, 80.0, 60.0),
                CategoryAverage("Pet", 0.0, 20.0, null),
            ),
            averages,
        )
        assertEquals(450.0, monthlyAverage(year, LocalDate.of(2026, 9, 15)), 0.001)
        assertEquals(50.0, monthlyAverage(year, LocalDate.of(2026, 9, 15), setOf("Dining")), 0.001)
    }

    @Test
    fun `compares no category in January, with no month of the year over`() {
        val year = report("Jan 2026" to mapOf("Groceries" to 300.0))
        assertEquals(emptyList<CategoryAverage>(), categoryAverages(year, LocalDate.of(2026, 1, 20)))
    }

    @Test
    fun `gives no percentage against nothing, or against refunds that outweighed the spending`() {
        assertEquals(null, percentChange(100.0, null))
        assertEquals(null, percentChange(100.0, 0.0))
        assertEquals(null, percentChange(300.0, -200.0))
        assertEquals(50.0, percentChange(150.0, 100.0)!!, 0.001)
    }

    @Test
    fun `formats money like the web app`() {
        assertEquals("$1,234.56", Money.format(1234.56))
        assertEquals("-$5.00", Money.format(-5.0))
        assertEquals("$0.00", Money.format(-0.001))
        assertEquals(listOf("$950", "$1.2K", "$5K", "-$2.5M"), listOf(950.0, 1234.0, 5000.0, -2_500_000.0).map(Money::compact))
        assertEquals(listOf("$1M", "$960K", "$1K", "$0"), listOf(999_960.0, 960_000.0, 999.6, -0.3).map(Money::compact))
        assertEquals(listOf("99%", "100%", "100%", "0%"), listOf(99.6, 100.0, 100.4, -3.0).map(Money::percentUsed))
    }
}
