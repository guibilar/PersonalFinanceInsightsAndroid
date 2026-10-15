package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.transactions.LinkNotice
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** A link to one transaction (T56): the Transactions page on its day and account, with the row highlighted. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionLinkTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)

    /** METRO 221, on 2026-10-03: the first row of the recorded list. */
    private val metro = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"
    private val metroDay = TransactionCriteria(accountId = "01a0eb35-f474-7573-a368-db81834d7a92", startDate = "2026-10-03", endDate = "2026-10-03")

    /** A row on the second page of the recorded list. */
    private val secondPage = "01a0ebd1-fb15-7d0b-9764-e122c41e95ec"
    private val unknown = "01a0ff22-0000-7000-8000-000000000000"

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model(id: String) = TransactionsViewModel(api, TransactionsRoute(transactionId = id)) { today }

    /** The list loads (not the look-ups): the filters without a transaction id. */
    private fun listFilters() = api.transactionFilters.filter { it.transactionId == null }

    @Test
    fun `a link opens the day and account of the transaction, and highlights it`() = runTest(dispatcher) {
        val model = model(metro)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(metroDay, state.criteria)
        assertEquals(metro, state.highlighted)
        assertNull(state.linkNotice)
        // The look-up first, then one list on the new filters (not a first list without them).
        assertEquals(metro, api.transactionFilters.first().transactionId)
        val list = listFilters().single()
        assertEquals(Triple(metroDay.accountId, "2026-10-03", "2026-10-03"), Triple(list.accountId, list.startDate, list.endDate))
        assertEquals(1, list.page)
        assertEquals("2026-10-03", api.breakdownInputs.last().startDate)
    }

    @Test
    fun `the highlight ends when the screen has shown it`() = runTest(dispatcher) {
        val model = model(metro)
        advanceUntilIdle()

        model.highlightShown("another")
        assertEquals(metro, model.state.value.highlighted)
        model.highlightShown(metro)
        assertNull(model.state.value.highlighted)
    }

    @Test
    fun `a row on a later page loads the next pages until it is found`() = runTest(dispatcher) {
        val model = model(secondPage)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), listFilters().map { it.page })
        assertEquals(secondPage, model.state.value.highlighted)
    }

    @Test
    fun `a deleted transaction shows on its day in the deleted list, highlighted (T65)`() = runTest(dispatcher) {
        api.deletedIds += metro
        val model = model(metro)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(LinkNotice.Deleted, state.linkNotice)
        assertTrue(state.showDeleted)
        assertEquals(metro, state.highlighted)
        assertTrue(state.list.items.any { it.id == metro })
        assertEquals(state.criteria.startDate, state.criteria.endDate)
        assertTrue(listFilters().last().hidden == true)
    }

    @Test
    fun `an unknown transaction shows the normal list with a message`() = runTest(dispatcher) {
        val model = model(unknown)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals("The linked transaction does not exist.", state.linkNotice?.text)
        assertTrue(state.criteria.isEmpty)
        assertEquals(25, state.list.items.size)
    }

    @Test
    fun `an id that is not a GUID is not looked up`() = runTest(dispatcher) {
        val model = model("not-an-id")
        advanceUntilIdle()

        assertEquals(LinkNotice.Missing, model.state.value.linkNotice)
        assertTrue(api.transactionFilters.all { it.transactionId == null })
        assertEquals(1, api.transactionFilters.size)
    }

    @Test
    fun `a failed look-up says so`() = runTest(dispatcher) {
        api.failing += "transactions"
        val model = model(metro)
        advanceUntilIdle()

        assertEquals("Could not load the linked transaction.", model.state.value.linkNotice?.text)
        // The normal list was asked for too (it failed the same way, with its own "Try again").
        assertEquals(1, listFilters().size)
    }

    @Test
    fun `the message can be closed, and goes when the filters change`() = runTest(dispatcher) {
        val model = model(unknown)
        advanceUntilIdle()
        model.dismissLinkNotice()
        assertNull(model.state.value.linkNotice)

        model.showTransaction(unknown)
        advanceUntilIdle()
        assertEquals(LinkNotice.Missing, model.state.value.linkNotice)
        model.setFlow(Flow.Refund)
        assertNull(model.state.value.linkNotice)
    }

    @Test
    fun `a link while the page is open changes the list to the transaction`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute(categoryId = "c1")) { today }
        advanceUntilIdle()
        model.search("metro")
        model.toggleDeleted()
        advanceUntilIdle()

        model.showTransaction(metro)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(metroDay, state.criteria)
        assertEquals("", state.search)
        assertFalse(state.showDeleted)
        assertEquals(metro, state.highlighted)
    }

    @Test
    fun `View after an add shows the new transaction`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.messages.collect { messages += it } }
        advanceUntilIdle()

        model.add(TransactionInput("2026-10-03", "METRO", 12.0, TransactionType.Debit, "a1"))
        advanceUntilIdle()
        val added = messages.single()
        assertEquals("View", added.actionLabel)
        added.action!!.invoke()
        advanceUntilIdle()

        assertEquals(metroDay, model.state.value.criteria)
        assertEquals(metro, model.state.value.highlighted)
    }
}
