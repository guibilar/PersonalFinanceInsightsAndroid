package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.AlertSamples
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Renders the screens with data recorded from a real backend, to look at (build/shots, uploaded by CI). Tall
 * windows, so a whole screen fits in one picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h2600dp-xxhdpi")
class ScreenshotTest {
    private val alerts = FakeAlertsApi()
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun dashboard() = dashboard(dark = false)

    @Test
    fun dashboardDark() = dashboard(dark = true)

    private fun dashboard(dark: Boolean) {
        val model = DashboardViewModel(api, alerts) { today }
        show(dark) { DashboardScreen(model, account, {}, PaddingValues(), {}) }
        shoot(if (dark) "dashboard-dark" else "dashboard")
        compose.onNodeWithText("Table").performClick()
        compose.waitForIdle()
        shoot(if (dark) "dashboard-table-dark" else "dashboard-table")
    }

    @Test
    fun dashboardWithAFailedReport() {
        api.failing += "yearReport"
        val model = DashboardViewModel(api, alerts) { today }
        show { DashboardScreen(model, account, {}, PaddingValues(), {}) }
        shoot("dashboard-failed")
    }

    @Test
    fun dashboardWithAllKindsOfAlerts() = dashboardWithAlerts("dashboard-alerts") { answer = AlertSamples.many }

    @Test
    fun dashboardWithNoAlerts() = dashboardWithAlerts("dashboard-no-alerts") { answer = AlertSamples.noMoney }

    @Test
    fun dashboardWhenTheAlertsFailed() = dashboardWithAlerts("dashboard-alerts-failed") { failing = true }

    @Test
    fun dashboardWhenSomeChecksFailed() = dashboardWithAlerts("dashboard-alerts-partial") { answer = AlertSamples.partial }

    /** The dashboard with the Money alerts that [setUp] gives (the server's "Needs attention" items). */
    private fun dashboardWithAlerts(name: String, setUp: FakeAlertsApi.() -> Unit) {
        alerts.setUp()
        val model = DashboardViewModel(api, alerts) { today }
        show { DashboardScreen(model, account, {}, PaddingValues(), {}) }
        shoot(name)
    }

    @Test
    fun transactions() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        show { TransactionsScreen(model, account, {}, PaddingValues()) }
        shoot("transactions")
    }

    @Test
    fun transactionsOverTime() = overTime(dark = false)

    @Test
    fun transactionsOverTimeDark() = overTime(dark = true)

    private fun overTime(dark: Boolean) {
        val model = TransactionsViewModel(
            api,
            TransactionsRoute(startDate = "2026-06-01", endDate = "2026-09-30", overTime = true, interval = BreakdownInterval.Month),
        ) { today }
        show(dark) { TransactionsScreen(model, account, {}, PaddingValues()) }
        compose.onNodeWithText("Show 8 more").performClick()
        compose.waitForIdle()
        shoot(if (dark) "transactions-over-time-dark" else "transactions-over-time")
    }

    @Test
    fun filters() {
        val model = TransactionsViewModel(api, TransactionsRoute(startDate = "2026-07-01", endDate = "2026-09-30")) { today }
        show { TransactionsScreen(model, account, {}, PaddingValues()) }
        compose.onNodeWithContentDescriptionCompat("Filters")
        compose.waitForIdle()
        shoot("filters")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionCompat(description: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(description)).performClick()
}
