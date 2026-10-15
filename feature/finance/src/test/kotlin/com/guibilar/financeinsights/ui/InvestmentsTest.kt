package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountBalanceInput
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.NetWorthScreen
import com.guibilar.financeinsights.ui.settings.NetWorthViewModel
import com.guibilar.financeinsights.ui.settings.parseBalance
import com.guibilar.financeinsights.ui.settings.roomText
import com.guibilar.financeinsights.ui.settings.sinceLastMonth
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Investment accounts, their plan and room, balances, and the net worth. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class InvestmentsTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val tfsa = Account(
        id = "tfsa", name = "Wealthsimple TFSA", type = AccountType.Investment, format = StatementFormat.Manual,
        transactionCount = 4, plan = RegisteredPlan.Tfsa, contributionRoom = 7000.0, contributionRoomYear = 2026,
        depositedThisYear = 3500.0, withdrawnThisYear = 400.0, roomLeft = 3500.0,
        balance = com.guibilar.financeinsights.core.model.AccountBalance("b1", "2026-09-30T00:00:00Z", 12_000.0),
    )

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = false, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    fun roomBalancesAndAmounts() {
        assertEquals("$3,500.00 of $7,000.00 room left this year · $3,500.00 in · $400.00 out (room again in January)", roomText(tfsa))
        assertEquals("Over by $100.00 · $7,100.00 in", roomText(tfsa.copy(roomLeft = -100.0, depositedThisYear = 7100.0, withdrawnThisYear = 0.0)))
        assertEquals("$0.00 deposited this year. Enter this year's room to see what's left.", roomText(tfsa.copy(roomLeft = null, depositedThisYear = 0.0)))
        assertEquals(listOf(1234.5, -20.0, null), listOf(parseBalance("1,234.50"), parseBalance("-20"), parseBalance("abc")))
        assertEquals(listOf("7000", "12.50"), listOf(Money.plain(7000.0), Money.plain(12.5)))
    }

    @Test
    fun accounts() {
        api.extraAccounts += tfsa
        val model = AccountsViewModel(api)
        show { AccountsScreen(model, PaddingValues(), onBack = {}) }

        compose.onNodeWithText("$3,500.00 of $7,000.00 room left this year", substring = true).assertExists()
        compose.onNodeWithText("Investment · TFSA · No files (add by hand) · 4 transactions").assertExists()
        shoot("accounts-investments")

        // Its balances: the ones entered, and a new one.
        compose.onNode(hasContentDescription("Balances of Wealthsimple TFSA")).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Oct 1, 2026").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Balance")).performTextInput("13000")
        shoot("account-balances")
        compose.onNodeWithText("Save balance").performClick()
        compose.waitForIdle()
        val (id, input) = api.sent.single { it.first == "setBalance" }.second as Pair<*, *>
        assertEquals("tfsa" to AccountBalanceInput(LocalDate.now().toString(), 13000.0), id to input)
        compose.onNodeWithText("Balance saved").assertExists()
    }

    @Test
    fun newInvestmentWithAPlan() {
        val model = AccountsViewModel(api)
        show { AccountsScreen(model, PaddingValues(), onBack = {}) }
        compose.onNodeWithText("New account", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Wealthsimple TFSA")
        // No plan for a chequing account.
        compose.onNodeWithText("Registered plan").assertDoesNotExist()
        compose.onNodeWithText("Investment").performClick()
        compose.onNodeWithText("TFSA").performClick()
        compose.onNode(hasSetTextAction() and hasText("Contribution room")).performTextInput("7000")
        shoot("account-tfsa")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()

        val sent = api.sent.single { it.first == "addAccount" }.second as AccountInput
        assertEquals(
            AccountInput("Wealthsimple TFSA", AccountType.Investment, StatementFormat.Cibc, RegisteredPlan.Tfsa, 7000.0, LocalDate.now().year),
            sent,
        )
    }

    @Test
    fun netWorth() {
        val model = NetWorthViewModel(api)
        var opened = false
        show { NetWorthScreen(model, PaddingValues(), onBack = {}, openAccounts = { opened = true }) }

        compose.onNodeWithText("$7,750.00").assertExists()
        compose.onNodeWithText("+$1,950.00 since last month").assertExists()
        compose.onNode(hasContentDescription("Visa, Credit card, owes $250.00, Entered Sep 30, 2026, moved since")).assertExists()
        compose.onNode(hasContentDescription("Wealthsimple, Investment · TFSA, $5,500.00, As of Sep 30, 2026")).assertExists()
        shoot("net-worth")

        compose.onNodeWithText("Add its balance").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun netWorthChangeNeedsTheSameAccounts() {
        val report = runBlocking { api.netWorth() }
        assertEquals(1950.0, sinceLastMonth(report)!!, 0.001)
        assertNull(sinceLastMonth(report.copy(months = report.months.take(2))))
    }
}
