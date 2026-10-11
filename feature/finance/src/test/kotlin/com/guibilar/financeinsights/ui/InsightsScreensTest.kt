package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.insights.RecurringScreen
import com.guibilar.financeinsights.ui.insights.RecurringViewModel
import com.guibilar.financeinsights.ui.insights.ReviewScreen
import com.guibilar.financeinsights.ui.insights.ReviewViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Subscriptions, the review, the dashboard's forecast, and changing several transactions at once. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class InsightsScreensTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 4)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    fun subscriptions() {
        var opened: String? = null
        val model = RecurringViewModel(api)
        show { RecurringScreen(model, PaddingValues(), onBack = {}, openTransactions = { opened = it }) }

        compose.onNodeWithText("$113.34").assertExists()
        compose.onNodeWithText("Up from $16.49").assertExists()
        compose.onNodeWithText("~$82.36").assertExists()
        shoot("subscriptions")

        compose.onNodeWithText("NETFLIX").performClick()
        assertEquals("NETFLIX", opened)
    }

    @Test
    fun review() {
        val model = ReviewViewModel(api) { today }
        show { ReviewScreen(model, PaddingValues(), onBack = {}, openSubscriptions = {}) }

        compose.onAllNodesWithText("September 2026")[0].assertExists()
        compose.onNodeWithText("88% less than August 2026").assertExists()
        shoot("review")

        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.waitForIdle()
        assertEquals(api.reviewRequests.toString(), 2026 to 8, api.reviewRequests.last())
    }

    @Test
    fun dashboardForecast() {
        var rules = 0
        val model = DashboardViewModel(api) { today }
        show { DashboardScreen(model, AccountInfo("dev", "s"), {}, PaddingValues(), {}, openRules = { rules++ }) }

        compose.onNodeWithText("Where the month is heading").assertExists()
        compose.onNodeWithText("$2,065.19").assertExists()
        compose.onNodeWithText("Budgets at this pace").performScrollTo()
        shoot("dashboard-forecast")

        compose.onNodeWithText("in Other because no rule names", substring = true).performScrollTo().performClick()
        assertEquals(1, rules)
    }

    @Test
    fun dashboardHeadlinesAttentionAndInvestments() {
        var imports = 0
        var netWorth = 0
        api.extraAccounts += Account(
            id = "ws", name = "Wealthsimple TFSA", type = AccountType.Investment, format = StatementFormat.Wealthsimple,
            transactionCount = 3, plan = RegisteredPlan.Tfsa, contributionRoom = 7000.0, contributionRoomYear = 2026,
            depositedThisYear = 5300.0, roomLeft = 1700.0, lastTransactionDate = "2026-06-16T00:00:00Z",
            balance = AccountBalance("b1", "2026-06-30T00:00:00Z", 4380.73),
        )
        val model = DashboardViewModel(api) { today }
        show {
            DashboardScreen(
                model, AccountInfo("dev", "s"), {}, PaddingValues(), {},
                openImport = { imports++ },
                openNetWorth = { netWorth++ },
            )
        }

        compose.onNodeWithText("Left over this month").assertExists()
        compose.onNodeWithText("Net worth").performClick()
        assertEquals(1, netWorth)
        compose.onNodeWithText("Needs attention").performScrollTo()
        shoot("dashboard-attention")
        compose.onNodeWithText("Wealthsimple TFSA: statements reach Jun 30, 2026. Import last month's").performClick()
        assertEquals(1, imports)

        // Further down the list: composed once scrolled to.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Balance on Jun 30, 2026 · $5,300.00 in this year · $1,700.00 room left"))
        shoot("dashboard-investments")
    }

    @Test
    fun aLongPressTicksATransaction_andAChangeGoesToAllTicked() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        show { TransactionsScreen(model, AccountInfo("dev", "s"), {}, PaddingValues()) }

        compose.onNode(hasContentDescription("METRO 221", substring = true)).performTouchInput { longClick() }
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        compose.onNodeWithText("2 selected").assertExists()
        shoot("transactions-selecting")

        compose.onNodeWithContentDescription("Set the category").performClick()
        compose.onAllNodesWithText("Dining")[0].performClick()
        compose.waitForIdle()

        val update = api.sent.single { it.first == "bulkUpdate" }.second as TransactionBulkUpdate
        assertEquals(2, update.transactionIds.size)
        compose.onNodeWithText("2 selected").assertDoesNotExist()
    }

    @Test
    fun keepInOther() {
        val model = RulesViewModel(api)
        show { RulesScreen(model, PaddingValues(), onBack = {}) }
        compose.onNodeWithText("Sort them").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Categorise as…")[0].performClick()
        compose.waitForIdle()
        shoot("unsorted-keep-in-other")

        compose.onNodeWithText("Keep in Other").performScrollTo().performClick()
        compose.waitForIdle()
        val rule = api.sent.first { it.first == "addCategoryRule" }.second as CategoryRuleInput
        assertEquals("Other", model.state.value.categories.first { it.id == rule.categoryId }.name)
    }
}
