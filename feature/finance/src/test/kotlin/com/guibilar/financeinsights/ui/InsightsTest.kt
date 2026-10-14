package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.logic.ReviewPeriod
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.insights.RecurringViewModel
import com.guibilar.financeinsights.ui.insights.ReviewViewModel
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow as KFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Recurring payments, the forecast, sorting what is in Other, changes to many transactions, and reviews. */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsTest {
    private val alerts = FakeAlertsApi()
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 4)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(messages: KFlow<UiMessage>): List<String> {
        val texts = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { messages.collect { texts += it.text } }
        return texts
    }

    @Test
    fun `the recurring payments load, and a failed refresh keeps them`() = runTest(dispatcher) {
        val model = RecurringViewModel(api)
        advanceUntilIdle()
        val report = (model.state.value as LoadState.Loaded).value
        assertEquals(listOf("ENMAX", "NETFLIX", "SPOTIFY"), report.coming.map { it.name })
        assertEquals(16.49, report.coming[1].priceChange!!.from, 0.0)

        api.failing += "recurring"
        model.refresh()
        advanceUntilIdle()
        assertEquals(report, (model.state.value as LoadState.Loaded).value)
        assertEquals(false, model.refreshing.value)
    }

    @Test
    fun `a review opens on the last complete month, and steps back and forth`() = runTest(dispatcher) {
        val model = ReviewViewModel(api) { today }
        advanceUntilIdle()
        assertEquals(listOf<Pair<Int?, Int?>>(null to null), api.reviewRequests)
        assertEquals(ReviewPeriod(2026, 8), model.previous())
        assertEquals(ReviewPeriod(2026, 10), model.next())

        model.go(model.previous())
        advanceUntilIdle()
        assertEquals(2026 to 8, api.reviewRequests.last())

        model.switchTo(year = true)
        advanceUntilIdle()
        assertEquals(2026 to null, api.reviewRequests.last())
    }

    @Test
    fun `a review stops at the first month with data`() = runTest(dispatcher) {
        // The recorded review's data starts in June 2026.
        val model = ReviewViewModel(api, ReviewPeriod(2026, 6)) { today }
        advanceUntilIdle()
        // The fixture answers as September; stepping is from what was answered.
        assertEquals(ReviewPeriod(2026, 8), model.previous())
        assertNull(ReviewPeriod(2026, 6).step(-1, today, "2026-06"))
    }

    @Test
    fun `the dashboard has where the month is heading`() = runTest(dispatcher) {
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()

        val forecast = (model.state.value.forecast as LoadState.Loaded).value
        assertEquals(2065.19, forecast.projected, 0.0)
        assertEquals(listOf("SPOTIFY", "ENMAX"), forecast.upcoming.map { it.name })
    }

    @Test
    fun `budgets show where they are heading`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()

        val projections = model.state.value.projections.values.associate { it.name to it.projectedPercentage }
        assertEquals(mapOf("Groceries" to 307.2, "Eating out" to 162.3), projections)
    }

    @Test
    fun `a merchant left in Other is sorted with a rule, then the rules applied`() = runTest(dispatcher) {
        val model = RulesViewModel(api)
        val messages = collect(model.messages)
        advanceUntilIdle()
        assertEquals(4, model.state.value.uncategorised!!.transactionCount)
        val dining = model.state.value.categories.first { it.name == "Dining" }

        assertNull(model.sortMerchant(" farmers fridge ", dining))
        advanceUntilIdle()

        assertEquals(
            listOf("addCategoryRule" to CategoryRuleInput("FARMERS FRIDGE", dining.id), "applyRules" to null),
            api.sent,
        )
        assertEquals(listOf("Rule “FARMERS FRIDGE” added for Dining: 3 transactions updated by the rules"), messages)
    }

    @Test
    fun `a merchant can be kept in Other on purpose, with a rule into Other`() = runTest(dispatcher) {
        val model = RulesViewModel(api)
        val messages = collect(model.messages)
        advanceUntilIdle()
        val other = model.state.value.categories.first { it.isFallback }

        assertNull(model.sortMerchant("mystery shop", other))
        advanceUntilIdle()

        assertEquals("addCategoryRule" to CategoryRuleInput("MYSTERY SHOP", other.id), api.sent.first())
        assertEquals(listOf("Rule “MYSTERY SHOP” added: it stays in Other and isn't listed here any more"), messages)
    }

    @Test
    fun `ticked transactions change at once, and the ticks go with it`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        val messages = collect(model.messages)
        advanceUntilIdle()
        val (first, second) = model.state.value.list.items
        model.toggleSelected(first.id)
        model.toggleSelected(second.id)
        model.toggleSelected(first.id)
        assertEquals(setOf(second.id), model.state.value.selected)

        model.selectAll()
        assertEquals(25, model.state.value.selected.size)
        model.bulk(TransactionBulkUpdate(emptyList(), flow = Flow.Transfer))
        advanceUntilIdle()

        val sent = api.sent.single { it.first == "bulkUpdate" }.second as TransactionBulkUpdate
        assertEquals(25 to Flow.Transfer, sent.transactionIds.size to sent.flow)
        assertTrue(model.state.value.selected.isEmpty())
        assertEquals(listOf("Updated 25 transactions"), messages)
    }

    @Test
    fun `a bulk change over 500 transactions goes in batches the API takes`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        val messages = collect(model.messages)
        advanceUntilIdle()
        (1..1200).forEach { model.toggleSelected("t$it") }

        model.bulk(TransactionBulkUpdate(emptyList(), flow = Flow.Transfer))
        advanceUntilIdle()

        val batches = api.sent.filter { it.first == "bulkUpdate" }.map { (it.second as TransactionBulkUpdate).transactionIds.size }
        assertEquals(listOf(500, 500, 200), batches)
        assertEquals(listOf("Updated 1200 transactions"), messages)
    }

    @Test
    fun `other filters forget what was ticked`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        advanceUntilIdle()
        model.toggleSelected(model.state.value.list.items.first().id)

        model.setFlow(Flow.Spending)
        assertTrue(model.state.value.selected.isEmpty())
    }

    @Test
    fun `a merchant's link opens the transactions on its search`() = runTest(dispatcher) {
        val model = TransactionsViewModel(api, TransactionsRoute(description = "NETFLIX")) { today }
        advanceUntilIdle()

        assertEquals("NETFLIX", model.state.value.search)
        assertEquals("NETFLIX", api.transactionFilters.first().description)
    }
}
