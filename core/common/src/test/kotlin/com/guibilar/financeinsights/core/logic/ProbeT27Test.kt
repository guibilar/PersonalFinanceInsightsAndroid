package com.guibilar.financeinsights.core.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Probe for T27: this test fails on purpose, to show the error annotation. Do not merge. */
class ProbeT27Test {
    @Test
    fun `this probe fails on purpose`() {
        assertEquals("The T27 probe expects this text", "and gets this text")
    }
}
