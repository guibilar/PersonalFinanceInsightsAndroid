package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A sheet open when the phone rotates stays open, with what was typed in it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class RotationTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    @Test
    fun `an open budget form survives rotating, with its edits`() {
        val model = BudgetsViewModel(api)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FinanceTheme(dynamicColor = false) { Surface { BudgetsScreen(model, account, {}, PaddingValues()) } } }
        compose.waitForIdle()
        compose.onNodeWithText("Groceries").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Description") and hasSetTextAction()).performTextReplacement("Weekly shop")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("Edit budget").assertExists()
        compose.onNode(hasText("Weekly shop") and hasSetTextAction()).assertExists()
    }

    @Test
    fun `a new rule keeps its chosen category when rotating`() {
        val model = RulesViewModel(api)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FinanceTheme(dynamicColor = false) { Surface { RulesScreen(model, PaddingValues()) {} } } }
        compose.waitForIdle()
        compose.onNodeWithText("New rule", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choose a category").performClick()
        // The menu's item, above the rules list's own "Dining" rows.
        compose.onAllNodesWithText("Dining").onLast().performClick()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("Sets the category").assertExists()
        // The read-only picker shows the category picked, not its prompt.
        compose.onNodeWithText("Choose a category").assertDoesNotExist()
    }
}
