package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.logic.DatePreset
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model(route: TransactionsRoute = TransactionsRoute()) = TransactionsViewModel(api, route) { today }

    @Test
    fun `opens on a link's criteria, with its chart`() = runTest(dispatcher) {
        val model = model(TransactionsRoute(categoryId = "c1", startDate = "2026-09-01", endDate = "2026-09-30", overTime = true, interval = BreakdownInterval.Week))
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.breakdownOpen)
        assertEquals("c1", api.transactionFilters.single().categoryId)
        assertEquals(BreakdownInterval.Week, api.breakdownInputs.single().interval)
        assertEquals("2026-09-30", api.breakdownInputs.single().endDate)
        assertEquals(25, state.list.items.size)
    }

    @Test
    fun `loads the next page as the list scrolls, once`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.loadMore()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), api.transactionFilters.map { it.page })
        assertEquals(50, model.state.value.list.items.size)
        assertTrue(model.state.value.list.endReached)
        model.loadMore()
        advanceUntilIdle()
        assertEquals(2, api.transactionFilters.size)
    }

    @Test
    fun `searches after a pause, and a filter change starts the list over`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.search("no f")
        model.search("no frills ")
        advanceTimeBy(TransactionsViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        model.setFlow(Flow.Refund)
        model.setDates(DatePreset.LastMonth.range(today))
        advanceUntilIdle()

        val last = api.transactionFilters.last()
        assertEquals("no frills", last.description)
        assertEquals(Flow.Refund, last.flow)
        assertEquals("2026-09-01" to "2026-09-30", last.startDate to last.endDate)
        assertEquals(1, last.page)
        // One request per pause in typing, not per letter; the flow's was replaced by the dates' before it went out.
        assertEquals(3, api.transactionFilters.size)
    }

    @Test
    fun `sorting the same column flips it, another starts its own way`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.sort(TransactionSortField.Date)
        model.sort(TransactionSortField.Description)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(TransactionSortField.Description to SortDirection.Ascending, state.sortBy to state.sortDirection)
        model.sort(TransactionSortField.Description)
        advanceUntilIdle()
        assertEquals(SortDirection.Descending, api.transactionFilters.last().sortDirection)
    }

    @Test
    fun `a period of the chart lists just its transactions, a step closer`() = runTest(dispatcher) {
        val model = model(TransactionsRoute(overTime = true))
        advanceUntilIdle()
        val period = model.state.value.breakdown.valueOrNull!!.periods.last()

        model.showPeriod(period)
        advanceUntilIdle()

        val input = api.breakdownInputs.last()
        assertEquals(BreakdownInterval.Week, input.interval)
        assertEquals(period.start.take(10) to period.end.take(10), input.startDate to input.endDate)
    }

    @Test
    fun `more merchants follow on from the top ones, and a new breakdown starts over`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        val top = model.state.value.merchants.size

        model.loadMoreMerchants()
        advanceUntilIdle()

        assertEquals(2, api.merchantInputs.single().page)
        assertEquals(top + 8, model.state.value.merchants.size)
        model.setInterval(BreakdownInterval.Week)
        advanceUntilIdle()
        assertEquals(top, model.state.value.merchants.size)
    }

    @Test
    fun `a failed first page says why, a failed later page keeps the list`() = runTest(dispatcher) {
        api.failing += "transactions"
        val model = model()
        advanceUntilIdle()
        assertNotNull(model.state.value.list.error)
        assertTrue(model.state.value.list.error!!.contains("Tailscale"))

        api.failing.clear()
        model.refresh()
        advanceUntilIdle()
        api.failing += "transactions"
        model.loadMore()
        advanceUntilIdle()

        val list = model.state.value.list
        assertEquals(25, list.items.size)
        assertTrue(list.moreFailed)
        assertFalse(list.loadingMore)
    }

    @Test
    fun `the deleted view drops the other rows at once, while new filters keep them until the new ones arrive`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setFlow(Flow.Spending)
        assertEquals(25, model.state.value.list.items.size)
        assertTrue(model.state.value.list.loading)
        advanceUntilIdle()

        model.toggleDeleted()
        assertTrue(model.state.value.list.items.isEmpty())
        assertTrue(model.state.value.list.loading)
    }

    @Test
    fun `back on a new day, today moves on and the list reloads`() = runTest(dispatcher) {
        var day = today
        val model = TransactionsViewModel(api, TransactionsRoute()) { day }
        advanceUntilIdle()
        model.resumed()
        advanceUntilIdle()
        assertEquals(1, api.transactionFilters.size)

        day = today.plusDays(1)
        model.resumed()
        advanceUntilIdle()
        assertEquals(today.plusDays(1), model.state.value.today)
        assertEquals(2, api.transactionFilters.size)
    }

    @Test
    fun `back on screen, the pickers reload, and a failed reload keeps what they had`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.resumed()
        val categories = model.state.value.categories
        assertTrue(categories.isNotEmpty())

        api.failing += "categories"
        model.resumed()
        advanceUntilIdle()
        assertEquals(categories, model.state.value.categories)
        assertEquals(1, api.transactionFilters.size)
    }

    @Test
    fun `a selected bar narrows where it went to that period, and tapping it again widens it`() = runTest(dispatcher) {
        val model = model(TransactionsRoute(overTime = true))
        advanceUntilIdle()
        val whole = model.state.value.merchants
        val period = model.state.value.breakdown.valueOrNull!!.periods.first()

        model.selectPeriod(0)
        advanceUntilIdle()
        val asked = api.merchantInputs.last()
        assertEquals(period.start.take(10), asked.startDate)
        assertEquals(period.end.take(10), asked.endDate)
        assertEquals(period, model.state.value.period)
        assertTrue(model.state.value.merchants.isNotEmpty())

        model.selectPeriod(null)
        assertEquals(whole, model.state.value.merchants)
    }
}
