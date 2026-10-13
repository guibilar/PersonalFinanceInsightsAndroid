package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.modules.MODULES
import com.guibilar.financeinsights.ui.modules.ModulesScreen
import com.guibilar.financeinsights.ui.shell.CaptureScreen
import com.guibilar.financeinsights.ui.shell.PlaceholderScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The top-level pages with recorded data, to look at (build/shots). The Money home is in the finance module's tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val day = LocalDate.of(2026, 10, 3)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun today() = today(dark = false)

    @Test
    fun todayDark() = today(dark = true)

    private fun today(dark: Boolean) {
        show(dark) { TodayScreen(DashboardViewModel(api) { day }, MODULES, PaddingValues(), open = {}, openMoney = {}) }
        compose.onNodeWithText("Saturday, October 3").assertExists()
        compose.onNodeWithText("Needs attention").assertExists()
        compose.onNodeWithText("Coming soon").assertExists()
        shoot(if (dark) "today-dark" else "today")
    }

    @Test
    fun `a Today tile opens its Money page`() {
        val opened = mutableListOf<MoneyLink>()
        show { TodayScreen(DashboardViewModel(api) { day }, MODULES, PaddingValues(), open = { opened += it }, openMoney = {}) }
        compose.onAllNodesWithText("Net worth").onFirst().performClick()
        assertEquals(listOf(MoneyLink.NetWorth), opened)
    }

    @Test
    fun modules() {
        val opened = mutableListOf<String>()
        show { ModulesScreen(MODULES, account, "1.0", PaddingValues(), open = { opened += it.id }, openSettings = {}, openAccount = {}) }
        shoot("modules")
        // Only Money opens; the others are coming soon.
        compose.onNodeWithText("Money").performClick()
        compose.onNodeWithText("Documents").performClick()
        assertEquals(listOf("money"), opened)
    }

    @Test
    fun capture() {
        show { CaptureScreen(PaddingValues(), onExpense = {}) }
        shoot("capture")
    }

    @Test
    fun timeline() {
        show { PlaceholderScreen("Timeline", Icons.Outlined.Timeline, "Everything that happened, day by day, from all modules.", PaddingValues()) }
        shoot("timeline")
    }
}
