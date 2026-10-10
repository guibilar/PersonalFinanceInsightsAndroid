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

/** On a tablet held sideways, the pages use the width: cards side by side, panes beside each other, centred columns. */
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
    fun `net worth's totals and accounts are beside each other`() {
        val model = NetWorthViewModel(api)
        show { NetWorthScreen(model, PaddingValues(), onBack = null, openAccounts = {}) }
        val total = compose.onNode(hasText("Owed on cards")).getUnclippedBoundsInRoot()
        val account = compose.onNode(hasContentDescription("Chequing", substring = true)).getUnclippedBoundsInRoot()
        assertTrue(account.left > total.right)
        assertTrue("the accounts start at the top, not under the chart", account.top < total.bottom)
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
    fun `the transactions keep a pane beside the list before one is picked`() {
        show { MainScreen(FakeFinanceApi(), account, {}) }
        compose.onNode(hasText("Transactions") and isSelectable()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pick a transaction", substring = true).assertExists()
        val listWidth = bounds("Search descriptions and notes").width
        shoot("tablet-transactions-empty-pane")
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Counts as").assertExists()
        compose.onNodeWithText("Pick a transaction", substring = true).assertDoesNotExist()
        assertEquals("the list keeps its width", listWidth, bounds("Search descriptions and notes").width)
    }
}
