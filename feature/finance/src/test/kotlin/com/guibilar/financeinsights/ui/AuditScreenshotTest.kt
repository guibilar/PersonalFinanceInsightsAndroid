package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Screens from the layout audit: the largest text size Android offers on a small phone (the unreachable server is the app's). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class AuditScreenshotTest {
    private val alerts = FakeAlertsApi()
    @get:Rule val compose = createComposeRule()

    @Test
    fun dashboardAtLargestText() {
        RuntimeEnvironment.setFontScale(2.0f)
        val model = DashboardViewModel(FakeFinanceApi(), alerts) { LocalDate.of(2026, 10, 3) }
        compose.setContent { FinanceTheme(ripples = false, dynamicColor = false) { Surface { DashboardScreen(model, AccountInfo("dev", "s"), {}, PaddingValues(), {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/audit-dashboard-font-2x.png")
    }
}
