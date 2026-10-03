package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.ui.settings.AccountSettingsScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.settings.AccountsScreen
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.CategoriesScreen
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.MoreScreen
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
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    fun more() {
        show { MoreScreen("dev", "0.3.0", PaddingValues(), {}, {}, {}, {}) }
        shoot("more")
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

    @Test
    fun accountSettings() {
        api.refusing["changePassword"] = ApiError.Http(400, "Invalid", null, null, mapOf("CurrentPassword" to listOf("Incorrect password.")))
        val model = AccountSettingsViewModel(api)
        show { AccountSettingsScreen(model, account, {}, PaddingValues()) {} }
        compose.onNode(hasText("Current password")).performTextInput("not it")
        compose.onNode(hasText("New password")).performTextInput("correct horse battery staple")
        compose.onNode(hasText("Repeat the new password")).performTextInput("correct horse battery staple")
        // The button, below the section heading of the same name.
        compose.onAllNodesWithText("Change password", useUnmergedTree = true).onLast().performClick()
        shoot("account-settings")
    }
}
