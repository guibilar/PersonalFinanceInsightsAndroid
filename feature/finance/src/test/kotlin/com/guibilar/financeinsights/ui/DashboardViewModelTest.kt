package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.AlertSamples
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.today.AlertFreshness
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.dashboard.MoneyAlerts
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val alerts = FakeAlertsApi()
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // No change in the app since the last fresh check: the first load takes the server's last check.
        AlertFreshness.checked(DataChanges.version)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `each report shows as it arrives, without waiting for a slow one`() = runTest(dispatcher) {
        api.slow["yearReport"] = 10_000
        val model = DashboardViewModel(api, alerts) { today }
        runCurrent()

        val state = model.state.value
        assertTrue(state.year is LoadState.Loading)
        assertTrue(state.month is LoadState.Loaded)
        assertTrue(state.budgets is LoadState.Loaded)
        assertTrue(state.recent is LoadState.Loaded)
        assertTrue(state.alerts is LoadState.Loaded)

        advanceTimeBy(10_001)
        advanceUntilIdle()
        assertTrue(model.state.value.year is LoadState.Loaded)
    }

    @Test
    fun `what needs attention is the server's Money alerts, in its order`() = runTest(dispatcher) {
        alerts.answer = AlertSamples.many
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()

        val loaded = model.state.value.alerts as LoadState.Loaded<MoneyAlerts>
        assertEquals(
            listOf("money.budget-over.b1", TodaySamples.priceChanged.id, TodaySamples.unsorted.id, "money.statements-not-imported"),
            loaded.value.items.map { it.id },
        )
        assertEquals(false, loaded.value.partial)
    }

    @Test
    fun `a partial check keeps its items, with a note`() = runTest(dispatcher) {
        alerts.answer = AlertSamples.partial
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()

        val loaded = model.state.value.alerts as LoadState.Loaded<MoneyAlerts>
        assertEquals(true, loaded.value.partial)
        assertEquals(2, loaded.value.items.size)
    }

    @Test
    fun `Money that the server could not check is a failure, with a retry that asks for a fresh check`() = runTest(dispatcher) {
        alerts.answer = AlertSamples.failed
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()
        assertEquals(LoadState.Failed(DashboardViewModel.ALERTS_FAILED), model.state.value.alerts)

        alerts.answer = AlertSamples.complete
        model.retryAlerts()
        advanceUntilIdle()
        assertTrue(model.state.value.alerts is LoadState.Loaded)
        assertEquals(listOf(false, true), alerts.calls)
    }

    @Test
    fun `a failed request is a failure, and the other cards still load`() = runTest(dispatcher) {
        alerts.failing = true
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()

        assertTrue(model.state.value.alerts is LoadState.Failed)
        assertTrue(model.state.value.forecast is LoadState.Loaded)
    }

    @Test
    fun `pull to refresh asks for a fresh check, a return without a change does not`() = runTest(dispatcher) {
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()
        model.resumed()
        model.resumed()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf(false, false, true), alerts.calls)
    }

    @Test
    fun `after a change in the app, the next load asks for a fresh check, one time`() = runTest(dispatcher) {
        val model = DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()
        model.resumed()

        DataChanges.changed()
        model.resumed()
        advanceUntilIdle()
        model.resumed()
        advanceUntilIdle()
        // A new screen after the fresh check: the stored alerts are fresh enough.
        DashboardViewModel(api, alerts) { today }
        advanceUntilIdle()

        assertEquals(listOf(false, true, false, false), alerts.calls)
    }
}
