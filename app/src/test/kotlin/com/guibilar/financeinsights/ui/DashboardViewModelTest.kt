package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each report shows as it arrives, without waiting for a slow one`() = runTest(dispatcher) {
        api.slow["yearReport"] = 10_000
        val model = DashboardViewModel(api) { LocalDate.of(2026, 10, 3) }
        runCurrent()

        val state = model.state.value
        assertTrue(state.year is LoadState.Loading)
        assertTrue(state.month is LoadState.Loaded)
        assertTrue(state.budgets is LoadState.Loaded)
        assertTrue(state.recent is LoadState.Loaded)

        advanceTimeBy(10_001)
        advanceUntilIdle()
        assertTrue(model.state.value.year is LoadState.Loaded)
    }
}
