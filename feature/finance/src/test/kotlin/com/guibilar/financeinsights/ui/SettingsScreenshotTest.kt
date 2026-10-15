package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.CategoriesScreen
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 3's screens with recorded data, to look at (build/shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    fun rules() {
        val model = RulesViewModel(api)
        show { RulesScreen(model, PaddingValues()) {} }
        shoot("rules")
        compose.onNodeWithText("GROCERY").performClick()
        shoot("rule-sheet")
    }

    @Test
    fun flowRulesDark() {
        val model = RulesViewModel(api)
        show(dark = true) { RulesScreen(model, PaddingValues()) {} }
        compose.onNodeWithText("Money in and transfers").performClick()
        shoot("flow-rules-dark")
    }

    @Test
    fun categories() {
        val model = CategoriesViewModel(api)
        show { CategoriesScreen(model, PaddingValues()) {} }
        shoot("categories")
        compose.onNodeWithText("Dining").performClick()
        shoot("category-sheet")
        compose.onNodeWithText("Delete category").performClick()
        shoot("category-delete")
    }

    @Test
    fun accounts() {
        val model = AccountsViewModel(api)
        show { AccountsScreen(model, PaddingValues()) {} }
        shoot("accounts")
        compose.onNodeWithText("Neo card").performClick()
        shoot("account-sheet")
    }
}
