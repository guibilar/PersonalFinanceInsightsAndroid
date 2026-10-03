package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.MoreScreen
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The screens at the largest text size Android offers on a small phone: nothing should break mid-word or clip. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class LargeTextScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    @Before fun big() = RuntimeEnvironment.setFontScale(2.0f)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/large-text-$name.png")
    }


    @Test fun transactions() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        show { TransactionsScreen(model, account, {}, PaddingValues()) }
        shoot("transactions")
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        shoot("edit")
    }

    @Test fun budgets() {
        val model = BudgetsViewModel(api)
        show { BudgetsScreen(model, account, {}, PaddingValues()) }
        shoot("budgets")
        compose.onNodeWithText("Groceries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Custom").performClick()
        shoot("budget-form")
    }

    @Test fun rules() {
        val model = RulesViewModel(api)
        show { RulesScreen(model, PaddingValues()) {} }
        shoot("rules")
    }

    @Test fun accounts() {
        val model = AccountsViewModel(api)
        show { AccountsScreen(model, PaddingValues()) {} }
        shoot("accounts")
    }

    @Test fun more() {
        show { MoreScreen("dev", "0.5.0", PaddingValues(), {}, {}, {}, {}) }
        shoot("more")
    }
}
