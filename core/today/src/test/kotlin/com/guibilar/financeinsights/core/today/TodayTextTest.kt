package com.guibilar.financeinsights.core.today

import com.guibilar.financeinsights.core.logic.Money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTextTest {
    @After
    fun showAmounts() {
        Money.hidden = { false }
    }

    @Test
    fun `formats each value type in the app's formats`() {
        assertEquals("$1,234.56", formatTodayValue(TodayValue.money(1234.56)))
        assertEquals("-$186.89", formatTodayValue(TodayValue.money(-186.89)))
        assertEquals("1,234", formatTodayValue(TodayValue.count(1234)))
        assertEquals("120%", formatTodayValue(TodayValue.percent(120.0)))
        assertEquals("12.5%", formatTodayValue(TodayValue.percent(12.5)))
        assertEquals("Jun 30, 2026", formatTodayValue(TodayValue.day("2026-06-30")))
        assertEquals("Aug 1–5", formatTodayValue(TodayValue.plain("Aug 1–5")))
    }

    @Test
    fun `a value type the app does not know gives its text`() {
        assertEquals("soon", formatTodayValue(TodayValue(TodayValueType.Unknown, text = "soon")))
        assertEquals("", formatTodayValue(TodayValue(TodayValueType.Unknown)))
    }

    @Test
    fun `hides amounts, and only amounts, when amounts are hidden`() {
        Money.hidden = { true }
        val values = mapOf(
            "payment" to TodayValue.plain("NETFLIX"),
            "from" to TodayValue.money(16.49),
            "to" to TodayValue.money(18.99),
            "count" to TodayValue.count(4),
        )
        assertEquals(
            "NETFLIX went up from ${Money.HIDDEN} to ${Money.HIDDEN}, 4 times",
            fillTodayText("{payment} went up from {from} to {to}, {count} times", values),
        )
        assertEquals(Money.HIDDEN, TodayValue.money(7750.0).let(::formatTodayValue))
    }

    @Test
    fun `fills each placeholder, and keeps one without a value`() {
        val values = mapOf("difference" to TodayValue.money(42.69))
        assertEquals("$42.69 over a usual month by now", fillTodayText("{difference} over a usual month by now", values))
        assertEquals("{missing} and $42.69", fillTodayText("{missing} and {difference}", values))
        assertEquals("", fillTodayText(null, values))
        assertEquals("No income yet this month", fillTodayText("No income yet this month", emptyMap()))
    }

    @Test
    fun `a tile without a value reads as a dash`() {
        val card = TodayCard("money.net-worth", "Money", TodayCardType.Tile, "net-worth", "Net worth", text = "Could not load this")
        assertEquals("—", card.valueText)
        assertEquals("Could not load this", card.filledText)
    }
}
