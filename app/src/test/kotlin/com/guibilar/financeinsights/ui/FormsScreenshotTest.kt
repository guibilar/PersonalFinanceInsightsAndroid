package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.ui.budgets.BudgetsScreen
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.ImportSheet
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The forms and sheets of phase 2, opened as a person would, to look at (build/shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class FormsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    private fun transactions(dark: Boolean = false) {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        show(dark) { TransactionsScreen(model, account, {}, PaddingValues()) }
    }

    @Test
    fun editWithARule() {
        transactions()
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Groceries").performClick()
        compose.onNodeWithText("Dining").performClick()
        compose.onNodeWithText("Also categorise similar transactions").performClick()
        compose.onNode(hasText("Also categorise similar transactions")).assertExists()
        compose.mainClock.advanceTimeBy(1000)
        shoot("edit-transaction")
    }

    @Test
    fun addADuplicate() {
        api.refusing["addTransaction"] = FakeFinanceApi.conflict("An identical transaction (same account, date, amount and description) already exists.")
        transactions(dark = true)
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Description")).performTextInput("COFFEE")
        compose.onNode(hasText("Amount")).performTextInput("4.50")
        // The sheet's button, above the screen's Add button.
        compose.onAllNodesWithText("Add").onLast().performClick()
        shoot("add-transaction-duplicate-dark")
    }

    @Test
    fun importLooksLikeAnotherAccounts() {
        api.refusing["importStatement"] = FakeFinanceApi.conflict("4 of the 5 transactions in this file are already in \"Neo card\". Is \"Credit card\" the right account?", "account-mismatch")
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        model.openImport(listOf(PickedFile("cibc_credit.csv", ByteArray(2048))))
        show {
            val state by model.state.collectAsState()
            ImportSheet(state.accounts, model, {})
        }
        compose.onNodeWithText("Choose an account").performClick()
        compose.onNodeWithText("Credit card · Credit card").performClick()
        compose.onNodeWithText("Import").performClick()
        shoot("import-mismatch")
    }

    @Test
    fun importSeveralFiles() {
        api.importAnswers += ImportResult(read = 1, added = 1, skipped = 0)
        api.importAnswers += ApiError.Http(400, "Bad Request", null, null, mapOf("file" to listOf("This file doesn't look like a Wealthsimple statement.")))
        api.importAnswers += ImportResult(read = 4, added = 3, skipped = 1)
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        model.openImport(listOf(PickedFile("May_2026.pdf", ByteArray(2048)), PickedFile("neo-statement.pdf", ByteArray(2048)), PickedFile("June_2026.pdf", ByteArray(2048))))
        show {
            val state by model.state.collectAsState()
            ImportSheet(state.accounts, model, {})
        }
        compose.onNodeWithText("Choose an account").performClick()
        compose.onNodeWithText("Credit card · Credit card").performClick()
        compose.onNodeWithText("Import 3 files").performScrollTo().performClick()
        // The uploads answer off the main thread.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Try again").fetchSemanticsNodes().isNotEmpty() }
        shoot("import-several")
    }

    @Test
    fun deletedTransactions() {
        transactions()
        compose.onNode(hasContentDescription("More")).performClick()
        compose.onNodeWithText("Deleted transactions").performClick()
        shoot("deleted-transactions")
    }

    @Test
    fun budgets() {
        val model = BudgetsViewModel(api)
        show { BudgetsScreen(model, account, {}, PaddingValues()) }
        shoot("budgets")
        compose.onNodeWithText("Groceries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Custom").performClick()
        shoot("budget-form")
    }

    @Test
    fun budgetsDark() {
        val model = BudgetsViewModel(api)
        show(dark = true) { BudgetsScreen(model, account, {}, PaddingValues()) }
        shoot("budgets-dark")
    }
}
