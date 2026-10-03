package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.signin.UnreachableScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Screens from the layout audit: the largest text size Android offers on a small phone, and the unreachable server. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class AuditScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dashboardAtLargestText() {
        RuntimeEnvironment.setFontScale(2.0f)
        val model = DashboardViewModel(FakeFinanceApi()) { LocalDate.of(2026, 10, 3) }
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { DashboardScreen(model, AccountInfo("dev", "s"), {}, PaddingValues(), {}) } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/audit-dashboard-font-2x.png")
    }

    @Test
    fun unreachableAtLaunch() {
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                UnreachableScreen("https://finance.example.ts.net", "Could not reach the server. Is Tailscale on?", {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/unreachable.png")
    }
}
