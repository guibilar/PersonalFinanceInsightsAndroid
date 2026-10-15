package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
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

/** T75: a read-only user sees the Money pages without write actions, and a quick action opens no form. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class ReadOnlyMoneyTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val account = AccountInfo("read-only token", "https://finance.example.ts.net")

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFinanceCanWrite provides false) { Surface { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun `transactions have no add, import, or apply rules, and a row opens the details only`() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        var addTaken = false
        show { TransactionsScreen(model, account, {}, PaddingValues(), addRequested = true, onAddTaken = { addTaken = true }) }

        assertEquals(true, addTaken)
        compose.onAllNodesWithContentDescription("Add transaction").assertCountEquals(0)
        compose.onAllNodesWithText(READ_ONLY_NOTE).onFirst().assertExists()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onAllNodesWithText("Import a statement").assertCountEquals(0)
        compose.onAllNodesWithText("Apply the rules").assertCountEquals(0)
        compose.onAllNodesWithText("Deleted transactions").onFirst().assertExists()
        shoot("transactions-read-only")
    }

    @Test
    fun `budgets have no new budget and no edit menu`() {
        val model = BudgetsViewModel(api)
        show { BudgetsScreen(model, account, {}, PaddingValues()) }
        compose.onAllNodesWithText("New budget").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("More for", substring = true).assertCountEquals(0)
        shoot("budgets-read-only")
    }
}
