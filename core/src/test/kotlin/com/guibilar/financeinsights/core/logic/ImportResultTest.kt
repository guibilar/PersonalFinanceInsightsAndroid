package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.importSummary
import org.junit.Test
import org.junit.Assert.assertEquals

class ImportResultTest {
    @Test
    fun `says what was added and skipped, and the balance a statement gave`() {
        assertEquals("Imported 1 new transaction", ImportResult(read = 1, added = 1, skipped = 0).message)
        assertEquals("Imported 7 new transactions (3 already imported)", ImportResult(read = 10, added = 7, skipped = 3).message)
        assertEquals(
            "Imported 0 new transactions; balance on May 31, 2026: $3,845.03",
            ImportResult(read = 0, added = 0, skipped = 0, balance = 3845.03, balanceDate = "2026-05-31T00:00:00Z").message,
        )
    }

    @Test
    fun `sums up several files, with the latest balance`() {
        assertEquals(
            "Imported 4 new transactions from 2 files (1 already imported); balance on Jun 30, 2026: $1,000.00",
            importSummary(
                listOf(
                    ImportResult(read = 4, added = 3, skipped = 1, balance = 1000.0, balanceDate = "2026-06-30T00:00:00Z"),
                    ImportResult(read = 1, added = 1, skipped = 0, balance = 900.0, balanceDate = "2026-05-31T00:00:00Z"),
                ),
            ),
        )
    }
}
