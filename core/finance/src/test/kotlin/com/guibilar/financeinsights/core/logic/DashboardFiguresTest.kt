package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.CashFlowMonth
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.StatementFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardFiguresTest {
    private val today = LocalDate.of(2026, 9, 5)

    @Test
    fun `this month against a usual one at the same point`() {
        // September 5th: a usual $3,000 month has cost 5/30 of it, $500, by now.
        assertEquals(SpentHint("$100.00 under a usual month by now", up = false, worse = false), usualHint(400.0, 3000.0, today))
        assertEquals(SpentHint("$250.00 over a usual month by now", up = true, worse = true), usualHint(750.0, 3000.0, today))
        assertEquals(SpentHint("about the same as a usual month by now", up = false, worse = false), usualHint(500.0, 3000.0, today))
        assertNull(usualHint(400.0, 0.0, today))
    }

    @Test
    fun `left over is this month's, or nothing before anything is recorded`() {
        val september = CashFlowMonth("Sep 2026", income = 4000.0, spending = 2600.0, net = 1400.0, savingsChange = 0.0)
        val report = CashFlowReport(months = listOf(CashFlowMonth("Aug 2026", 1.0, 1.0, 0.0, 0.0), september))

        assertEquals(september, leftOverThisMonth(report, today))
        assertNull(leftOverThisMonth(CashFlowReport(months = listOf(CashFlowMonth("Aug 2026", 1.0, 1.0, 0.0, 0.0))), today))
    }

    @Test
    fun `investments, by balance, with this year's room`() {
        val tfsa = Account(
            id = "a1", name = "TFSA", type = AccountType.Investment, format = StatementFormat.Wealthsimple,
            plan = RegisteredPlan.Tfsa, depositedThisYear = 5300.0, roomLeft = 1700.0,
            balance = AccountBalance("b1", "2026-08-31T00:00:00Z", 4380.73),
        )
        val brokerage = Account(id = "a2", name = "Brokerage", type = AccountType.Investment, format = StatementFormat.Manual)
        val chequing = Account(id = "Chequing", name = "Chequing", type = AccountType.Chequing, format = StatementFormat.Cibc)

        assertEquals(listOf("TFSA", "Brokerage"), investments(listOf(brokerage, chequing, tfsa)).map { it.name })
        assertEquals("Balance on Aug 31, 2026 · $5,300.00 in this year · $1,700.00 room left", investmentLine(tfsa))
        assertEquals("Balance on Aug 31, 2026 · $5,300.00 in this year · over by $100.00", investmentLine(tfsa.copy(roomLeft = -100.0)))
        assertEquals("No balance yet", investmentLine(brokerage))
    }
}
