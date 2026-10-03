package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Coming back to a screen while offline keeps what it showed, instead of turning it into errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeOfflineTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the dashboard keeps its figures when the reload on return fails`() = runTest(dispatcher) {
        val model = DashboardViewModel(api) { LocalDate.of(2026, 10, 3) }
        advanceUntilIdle()
        model.resumed()

        api.failing += listOf("yearReport", "monthReport", "cashFlow", "budgets", "transactions")
        model.resumed()
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.year is LoadState.Loaded)
        assertTrue(state.month is LoadState.Loaded)
        assertTrue(state.cashFlow is LoadState.Loaded)
        assertTrue(state.budgets is LoadState.Loaded)
        assertTrue(state.recent is LoadState.Loaded)
    }

    @Test
    fun `the budgets keep their list when the reload on return fails`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()
        model.resumed()

        api.failing += "budgets"
        model.resumed()
        advanceUntilIdle()

        assertTrue(model.state.value.items.isNotEmpty())
        assertNull(model.state.value.error)
    }
}
