package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.RulesResult
import com.guibilar.financeinsights.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormsTest {
    @Test
    fun `reads amounts as typed, in whole cents`() {
        assertEquals(listOf(12.5, 1234.56, 40.0, 0.1), listOf("12.5", "1,234.56", "$40", ".10").map(::parseAmount))
        assertNull(parseAmount("12.505"))
        assertNull(parseAmount("-5"))
        assertNull(parseAmount("abc"))
        assertEquals("Use whole cents: at most 2 decimal places.", amountError("12.505"))
        assertEquals("The amount must be more than zero.", amountError("0"))
        assertEquals("Enter an amount.", amountError(" "))
        assertNull(amountError("0.1"))
    }

    @Test
    fun `money out can't be income, money in can't be spending`() {
        assertEquals(listOf(Flow.Spending, Flow.Transfer, Flow.Ignored), flowsFor(TransactionType.Debit))
        assertEquals(listOf(Flow.Refund, Flow.Income, Flow.Transfer, Flow.Ignored), flowsFor(TransactionType.Credit))
    }

    @Test
    fun `suggests the merchant as a rule keyword`() {
        assertEquals("CORNER STORE", suggestRuleKeyword("SQ *CORNER STORE #123 TORONTO ON"))
        assertEquals("PIZZA PLACE", suggestRuleKeyword("TST-PIZZA PLACE 4471"))
        assertEquals("NO FRILLS", suggestRuleKeyword("no frills 3121"))
        assertEquals("12345", suggestRuleKeyword("12345"))
    }

    @Test
    fun `describes budgets and rule results like the web`() {
        val custom = Budget("b", "Trip", period = BudgetPeriod.Custom, startDate = "2026-07-01T00:00:00Z", endDate = "2026-08-15T00:00:00Z")
        assertEquals(" · Jul 1 – Aug 15, 2026", periodSuffix(custom))
        assertEquals(" this month", periodSuffix(custom.copy(period = BudgetPeriod.Monthly)))
        assertNull(periodName(BudgetPeriod.Total))
        assertEquals(BudgetHealth.Warning, budgetHealth(80.0))
        assertEquals("Updated 3 of 40 transactions", describeRulesResult(RulesResult(40, 3)))
        assertEquals("All 40 transactions already match the rules", describeRulesResult(RulesResult(40, 0)))
    }
}
