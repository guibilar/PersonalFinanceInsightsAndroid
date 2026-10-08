package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.StatementFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ImportReminderTest {
    private val toronto = ZoneId.of("America/Toronto")

    private fun account(name: String, imported: String? = null, last: String? = null, format: StatementFormat = StatementFormat.Cibc) =
        Account(id = name, name = name, type = AccountType.Chequing, format = format, lastImportedAt = imported, lastTransactionDate = last)

    private val accounts = listOf(
        account("Chequing", imported = "2026-09-28T15:00:00Z"),
        // Late in the evening in Toronto is already the next day in UTC: the reminder counts the local day.
        account("Visa", imported = "2026-09-30T02:30:00Z"),
        account("Cash", imported = "2026-10-05T12:00:00Z", format = StatementFormat.Manual),
    )

    @Test
    fun `the last import is the latest into an account that takes statements, on the local day`() {
        assertEquals(LocalDate.of(2026, 9, 29), lastImportDay(accounts, toronto))
    }

    @Test
    fun `before imports were recorded, the latest transaction stands in, and nothing ever imported says nothing`() {
        assertEquals(LocalDate.of(2026, 9, 2), lastImportDay(listOf(account("Old", last = "2026-09-02T00:00:00Z")), toronto))
        assertNull(lastImportDay(listOf(account("New")), toronto))
        assertNull(importReminder(listOf(account("New")), LocalDate.of(2026, 10, 30), toronto, emptyMap()))
    }

    @Test
    fun `reminds after more than seven days, once, and again each week while nothing is imported`() {
        // Last import Sep 29: Oct 6 is 7 days on, Oct 7 is 8.
        assertNull(importReminder(accounts, LocalDate.of(2026, 10, 6), toronto, emptyMap()))

        val first = importReminder(accounts, LocalDate.of(2026, 10, 7), toronto, emptyMap())!!
        assertEquals("No statements imported in 8 days", first.title)
        assertEquals(
            "The last import was Sep 29. Import your latest statements to keep budgets and net worth up to date.",
            first.text,
        )

        val sent = mapOf(first.key to 1)
        assertNull(importReminder(accounts, LocalDate.of(2026, 10, 13), toronto, sent))
        val second = importReminder(accounts, LocalDate.of(2026, 10, 14), toronto, sent)!!
        assertEquals("No statements imported in 15 days", second.title)
    }

    @Test
    fun `a new import starts the count again`() {
        val again = accounts + account("Neo", imported = "2026-10-10T16:00:00Z")

        assertNull(importReminder(again, LocalDate.of(2026, 10, 14), toronto, emptyMap()))
        assertEquals("No statements imported in 8 days", importReminder(again, LocalDate.of(2026, 10, 18), toronto, emptyMap())!!.title)
    }
}
