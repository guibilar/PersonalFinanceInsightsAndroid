package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.TransactionCriteria
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionLookupTest {
    private val api = FakeFinanceApi()

    /** A recorded transaction: METRO 221 on 2026-10-03, in the account below. */
    private val metro = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"
    private val metroAccount = "01a0eb35-f474-7573-a368-db81834d7a92"
    private val unknown = "01a0ff22-0000-7000-8000-000000000000"

    @Test
    fun `a live transaction is found with one call`() = runTest {
        val found = api.findTransaction(metro)

        assertTrue(found is TransactionLookup.Found)
        assertEquals(metro, (found as TransactionLookup.Found).transaction.id)
        val filter = api.transactionFilters.single()
        assertEquals(metro to 1, filter.transactionId to filter.pageSize)
        assertEquals(null, filter.hidden)
    }

    @Test
    fun `a deleted transaction is found among the deleted ones`() = runTest {
        api.deletedIds += metro

        val found = api.findTransaction(metro)

        assertTrue(found is TransactionLookup.Deleted)
        assertEquals(listOf(null, true), api.transactionFilters.map { it.hidden })
    }

    @Test
    fun `an id that no transaction has is missing`() = runTest {
        assertEquals(TransactionLookup.Missing, api.findTransaction(unknown))
        assertEquals(2, api.transactionFilters.size)
    }

    @Test
    fun `an id that is not a GUID is missing, with no call`() = runTest {
        listOf("gone", "", "01a0ff22-674a-7e98-bb70", "$metro/x").forEach { id ->
            assertEquals(id, TransactionLookup.Missing, api.findTransaction(id))
        }
        assertTrue(api.transactionFilters.isEmpty())
        assertTrue(isTransactionId(metro.uppercase()))
        assertFalse(isTransactionId(" $metro"))
    }

    @Test
    fun `a failed look-up says so, and does not ask for the deleted ones`() = runTest {
        api.failing += "transactions"
        assertEquals(TransactionLookup.Failed, api.findTransaction(metro))
        assertEquals(1, api.transactionFilters.size)

        api.failing.clear()
        api.refusing["transactions"] = ApiError.Http(500, "Server error", null, null, emptyMap())
        assertEquals(TransactionLookup.Failed, api.findTransaction(metro))
    }

    @Test
    fun `the link shows the day and the account of the transaction, and nothing more`() = runTest {
        val found = api.findTransaction(metro) as TransactionLookup.Found

        assertEquals(
            TransactionCriteria(accountId = metroAccount, startDate = "2026-10-03", endDate = "2026-10-03"),
            linkCriteria(found.transaction),
        )
    }
}
