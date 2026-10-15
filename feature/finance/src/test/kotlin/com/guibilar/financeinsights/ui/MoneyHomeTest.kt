package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.AlertSamples
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.filledText
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.money.MoneyHomeScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Money module's home: the four tiles, what needs attention (the server's Money alerts, the same items as on
 * Today), and a row for each finance page that opens it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class MoneyHomeTest {
    private val alerts = FakeAlertsApi()
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun home(dark: Boolean = false, open: (Any) -> Unit = {}, openImport: () -> Unit = {}): DashboardViewModel {
        val model = DashboardViewModel(api, alerts) { today }
        show(dark) { MoneyHomeScreen(model, PaddingValues(), onBack = {}, open = open, openImport = openImport) }
        return model
    }

    @Test
    fun money() {
        home()
        shoot("money")
    }

    @Test
    fun moneyDark() {
        home(dark = true)
        shoot("money-dark")
    }

    @Test
    fun `money with all kinds of alerts`() {
        alerts.answer = AlertSamples.many
        home()
        shoot("money-alerts")
    }

    @Test
    fun `money with no alerts`() {
        alerts.answer = AlertSamples.noMoney
        home()
        compose.onNodeWithText("Needs attention").assertDoesNotExist()
        shoot("money-no-alerts")
    }

    @Test
    fun `money when the alerts could not load`() {
        alerts.failing = true
        home()
        // The message says why (here: the server cannot be reached), with "Try again".
        compose.onNodeWithText("Needs attention").assertExists()
        shoot("money-alerts-failed")

        alerts.failing = false
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("NETFLIX went up from $16.49 to $18.99").assertExists()
        // "Try again" asks the server for a fresh check.
        assertEquals(true, alerts.calls.last())
    }

    @Test
    fun `money when the server could not check Money`() {
        alerts.answer = AlertSamples.failed
        home()
        compose.onNodeWithText("Could not check what needs attention.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `money when some checks could not run`() {
        alerts.answer = AlertSamples.partial
        home()
        compose.onNodeWithText("Some checks could not run. This list can be out of date.").assertExists()
        compose.onNodeWithText("NETFLIX went up from $16.49 to $18.99").assertExists()
        shoot("money-alerts-partial")
    }

    @Test
    fun `shows the same Money items as Today, and not the other modules' alerts`() {
        home()
        val today = TodaySamples.complete.cards.filter { it.type == TodayCardType.Attention && it.module == "Money" }
        today.forEach { compose.onNodeWithText(it.filledText).assertExists() }
        compose.onNodeWithText(AlertSamples.documentExpiring.title).assertDoesNotExist()
        compose.onNodeWithText("Lease expires on Oct 20, 2026").assertDoesNotExist()
    }

    @Test
    fun `each item opens where it is dealt with`() {
        alerts.answer = AlertSamples.many
        val opened = mutableListOf<Any>()
        var imports = 0
        home(open = { opened += it }, openImport = { imports++ })
        listOf(
            "Groceries is over its limit (112% used)",
            "NETFLIX went up from $16.49 to $18.99",
            "4 transactions are in Other because no rule names their merchants",
            "No statements imported in 9 days",
        ).forEach { compose.onNodeWithText(it).performScrollTo().performClick() }

        assertEquals(listOf(BudgetsRoute, SubscriptionsRoute, RulesRoute), opened)
        assertEquals(1, imports)
    }

    @Test
    fun `a statement item opens the import with its account`() {
        val behind = AlertSamples.many.alerts.last().copy(
            id = "money.statements-behind.a1", kind = "statements-behind", text = "Visa: statements reach Aug 31",
            values = emptyMap(), link = "/money/transactions?importAccount=a1#import",
        )
        alerts.answer = AlertSamples.complete.copy(alerts = listOf(behind))
        val accounts = mutableListOf<String?>()
        val model = DashboardViewModel(api, alerts) { today }
        show {
            MoneyHomeScreen(model, PaddingValues(), onBack = {}, open = {}, openImport = {}, openAccountImport = { accounts += it })
        }
        compose.onNodeWithText("Visa: statements reach Aug 31").performScrollTo().performClick()
        assertEquals(listOf<String?>("a1"), accounts)
    }

    @Test
    fun `each row opens its page`() {
        val opened = mutableListOf<Any>()
        alerts.answer = AlertSamples.noMoney
        home(open = { opened += it })
        listOf("Dashboard", "Review", "Transactions", "Subscriptions", "Accounts", "Categories", "Tags", "Rules").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        assertEquals(
            listOf(DashboardRoute, ReviewRoute(), TransactionsRoute(), SubscriptionsRoute, AccountsRoute, CategoriesRoute, TagsRoute, RulesRoute),
            opened,
        )
    }

    @Test
    fun `import opens the transactions with the import`() {
        var imports = 0
        home(openImport = { imports++ })
        compose.onNode(hasContentDescription("Import statements")).performClick()
        assertEquals(1, imports)
    }
}
