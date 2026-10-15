package com.guibilar.financeinsights.core.logic

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
