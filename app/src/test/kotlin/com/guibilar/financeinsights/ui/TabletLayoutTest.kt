package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.settings.MoreScreen
import com.guibilar.financeinsights.ui.settings.NetWorthScreen
import com.guibilar.financeinsights.ui.settings.NetWorthViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** On a tablet held sideways, the pages use the width: cards side by side, tiles in a row, centred columns. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-mdpi")
class TabletLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")
    private val today = LocalDate.of(2026, 10, 3)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    private fun bounds(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot()

    @Test
    fun `the dashboard's cards sit in two columns, the tiles in one row`() {
        val model = DashboardViewModel(api) { today }
        show { DashboardScreen(model, account, {}, PaddingValues(), {}) }
        val forecast = bounds("Where the month is heading")
        val byCategory = bounds("By category")
        assertTrue("side by side", byCategory.left > forecast.right)
        assertTrue("in the same row", byCategory.top < forecast.bottom)
        assertEquals("the four tiles in one row", bounds("Spent this month").top, bounds("Budgets").top)
        // The page stops growing past a glance: centred in the window.
        assertTrue(forecast.left > 16.dp)
        shoot("tablet-dashboard")
    }

    @Test
    fun `budgets are cards side by side`() {
        val model = BudgetsViewModel(api)
        show { BudgetsScreen(model, account, {}, PaddingValues()) }
        val first = bounds("Eating out")
        val second = bounds("Groceries")
        assertEquals("one row", first.top, second.top)
        assertTrue(second.left > first.right)
        shoot("tablet-budgets")
    }

    @Test
    fun `net worth's figures are tiles in a row, over the months and the accounts`() {
        val model = NetWorthViewModel(api)
        show { NetWorthScreen(model, PaddingValues(), onBack = null, openAccounts = {}) }
        val assets = bounds("Assets")
        assertEquals("one row", assets.top, bounds("Owed on cards").top)
        val months = bounds("Over the last twelve months")
        val accounts = bounds("Accounts")
        assertTrue(months.top > assets.bottom && accounts.top > months.bottom)
        assertTrue("a centred column", assets.left > 200.dp)
        shoot("tablet-net-worth")
    }

    @Test
    fun `the More page is a centred column`() {
        show { MoreScreen("dev", "1.0", PaddingValues(), {}, {}, {}, {}) }
        val entry = bounds("Subscriptions")
        assertTrue("not at the window's edge", entry.left > 200.dp)
        shoot("tablet-more")
    }

    @Test
    fun `a transaction opens over a centred list`() {
        show { MainScreen(FakeFinanceApi(), account, {}) }
        compose.onNode(hasText("Transactions") and isSelectable()).performClick()
        compose.waitForIdle()
        assertTrue("a centred column", bounds("Search descriptions and notes").left > 200.dp)
        shoot("tablet-transactions")
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Counts as").assertExists()
        shoot("tablet-transaction-sheet")
    }
}
