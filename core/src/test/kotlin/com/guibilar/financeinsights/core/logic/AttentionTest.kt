package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.CashFlowMonth
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.PriceChange
import com.guibilar.financeinsights.core.model.RecurringCadence
import com.guibilar.financeinsights.core.model.RecurringPayment
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.RecurringStatus
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AttentionTest {
    private val today = LocalDate.of(2026, 9, 5)

    private fun account(
        name: String,
        format: StatementFormat = StatementFormat.Cibc,
        last: String? = null,
        balanceDate: String? = null,
    ) = Account(
        id = name, name = name, type = AccountType.Chequing, format = format, transactionCount = if (last != null) 3 else 0,
        lastTransactionDate = last, balance = balanceDate?.let { AccountBalance("b", it, 100.0) },
    )

    @Test
    fun `this month against a usual one at the same point`() {
        // September 5th: a usual $3,000 month has cost 5/30 of it, $500, by now.
        assertEquals(SpentHint("$100.00 under a usual month by now", up = false, worse = false), usualHint(400.0, 3000.0, today))
        assertEquals(SpentHint("$250.00 over a usual month by now", up = true, worse = true), usualHint(750.0, 3000.0, today))
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
    fun `an account is behind when its statements stop before last month, by transactions or balances`() {
        val accounts = listOf(
            account("TFSA", StatementFormat.Wealthsimple, last = "2026-06-16T00:00:00Z", balanceDate = "2026-06-30T00:00:00Z"),
            // A quiet month still brings a balance for its last day.
            account("RRSP", StatementFormat.Wealthsimple, last = "2026-03-02T00:00:00Z", balanceDate = "2026-08-31T00:00:00Z"),
            account("Chequing", last = "2026-09-02T00:00:00Z"),
            account("Cash", StatementFormat.Manual, last = "2026-01-02T00:00:00Z"),
            account("Unused"),
        )

        assertEquals(listOf("TFSA"), accountsBehind(accounts, today).map { it.name })
        assertEquals(LocalDate.of(2026, 6, 30), statementsReach(accounts[0]))
    }

    @Test
    fun `lists budgets over, statements behind, recent price changes and unsorted transactions`() {
        val budget = Budget(
            id = "b1", name = "Groceries", status = Status.Active, period = BudgetPeriod.Monthly, totalAmount = 150.0,
            totalSpent = 180.0, totalRemaining = -30.0, percentageUsed = 120.0,
        )
        fun payment(name: String, since: String, status: RecurringStatus = RecurringStatus.Active) = RecurringPayment(
            name = name, cadence = RecurringCadence.Monthly, status = status, amount = 18.99,
            firstDate = "2026-01-05T00:00:00Z", lastDate = since, lastAmount = 18.99, nextDate = "2026-10-05T00:00:00Z",
            count = 9, monthlyCost = 18.99, yearlyCost = 227.88, priceChange = PriceChange(16.49, 18.99, since),
        )
        val recurring = RecurringReport(
            payments = listOf(
                payment("NETFLIX", "2026-08-20T00:00:00Z"),
                payment("OLD GYM", "2026-03-20T00:00:00Z"),
                payment("STOPPED", "2026-08-25T00:00:00Z", RecurringStatus.Stopped),
            ),
        )

        val items = attentionItems(
            unsorted = 1,
            budgets = listOf(budget),
            recurring = recurring,
            accounts = listOf(account("TFSA", StatementFormat.Wealthsimple, balanceDate = "2026-06-30T00:00:00Z")),
            today = today,
        )

        assertEquals(
            listOf(
                AttentionItem(AttentionKind.Budget, "Groceries is over its limit (120% used)"),
                AttentionItem(AttentionKind.Statement, "TFSA: statements reach Jun 30, 2026. Import last month's"),
                AttentionItem(AttentionKind.Price, "NETFLIX went up from $16.49 to $18.99"),
                AttentionItem(AttentionKind.Unsorted, "1 transaction is in Other because no rule names its merchant"),
            ),
            items,
        )
    }

    @Test
    fun `investments, by balance, with this year's room`() {
        val tfsa = Account(
            id = "a1", name = "TFSA", type = AccountType.Investment, format = StatementFormat.Wealthsimple,
            plan = RegisteredPlan.Tfsa, depositedThisYear = 5300.0, roomLeft = 1700.0,
            balance = AccountBalance("b1", "2026-08-31T00:00:00Z", 4380.73),
        )
        val brokerage = Account(id = "a2", name = "Brokerage", type = AccountType.Investment, format = StatementFormat.Manual)
        val chequing = account("Chequing")

        assertEquals(listOf("TFSA", "Brokerage"), investments(listOf(brokerage, chequing, tfsa)).map { it.name })
        assertEquals("Balance on Aug 31, 2026 · $5,300.00 in this year · $1,700.00 room left", investmentLine(tfsa))
        assertEquals("Balance on Aug 31, 2026 · $5,300.00 in this year · over by $100.00", investmentLine(tfsa.copy(roomLeft = -100.0)))
        assertEquals("No balance yet", investmentLine(brokerage))
    }
}
