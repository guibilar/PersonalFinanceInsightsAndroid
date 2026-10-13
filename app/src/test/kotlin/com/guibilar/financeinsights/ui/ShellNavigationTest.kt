package com.guibilar.financeinsights.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.fakeApiClient
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's top-level pages, and how the Money pages open and close: from Modules, from Today, and from outside the
 * app (the shortcut, the widget, the notifications, Capture). Back from a Money page returns to the Money home, and
 * then to the page it was opened from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class ShellNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")
    private val quickAction = MutableStateFlow<QuickAction?>(null)

    private fun show() {
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { MainScreen(fakeApiClient(FakeFinanceApi()), account, {}, quickAction = quickAction) } } }
        compose.waitForIdle()
    }

    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertShows(page: String, text: String) = assertTrue("$page shows \"$text\"", shows(text))

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun quick(action: QuickAction) {
        quickAction.value = action
        compose.waitForIdle()
    }

    // What shows only on these pages.
    private val today = "Money this month"
    private val moneyHome = "Overview"
    private val modules = "Spending, budgets and net worth"

    @Test
    fun `the app opens on Today`() {
        show()
        assertShows("Today", today)
        tab("Today").assertIsSelected()
    }

    @Test
    fun `Modules opens Money, and Back returns through the Money home to Modules and Today`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        assertShows("Modules", modules)
        compose.onNodeWithText("Money").performClick()
        compose.waitForIdle()
        assertShows("the Money home", moneyHome)
        compose.onNodeWithText("Transactions").performClick()
        compose.waitForIdle()
        assertShows("the transactions", "Search descriptions and notes")
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Money home", moneyHome)
        back()
        assertShows("Modules", modules)
        back()
        assertShows("Today", today)
    }

    @Test
    fun `a Today tile opens its Money page, and Back returns to the Money home, then Today`() {
        show()
        compose.onAllNodesWithText("Net worth").onFirst().performClick()
        compose.waitForIdle()
        assertShows("net worth", "Net worth")
        assertTrue("not Today", !shows(today))
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Money home", moneyHome)
        back()
        assertShows("Today", today)
    }

    @Test
    fun `each quick action opens its page over the Money home`() {
        show()
        val pages = listOf(
            QuickAction.Dashboard to "Where the month is heading",
            QuickAction.Budgets to "Budgets",
            QuickAction.Subscriptions to "Subscriptions",
            QuickAction.Review to "Review",
            QuickAction.Import to "Import statements",
        )
        pages.forEach { (action, text) ->
            quick(action)
            assertShows("$action", text)
            tab("Modules").assertIsSelected()
            // (A sheet takes Back in its own window; here Back leaves the page with it.)
            back()
            assertShows("the Money home after $action", moneyHome)
            back()
            assertShows("Today after $action", today)
        }
    }

    @Test
    fun `the shortcut opens the Add transaction form`() {
        show()
        quick(QuickAction.AddTransaction)
        assertShows("the add form", "Add a transaction")
    }

    @Test
    fun `Capture's Expense opens the Add transaction form, and the other buttons are off`() {
        show()
        tab("Capture").performClick()
        compose.waitForIdle()
        listOf("Photo", "Scan", "Voice").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        compose.onNodeWithText("Expense").performClick()
        compose.waitForIdle()
        assertShows("the add form", "Add a transaction")
    }

    @Test
    fun `Modules again, from a Money page opened on Today, opens the Modules page`() {
        show()
        quick(QuickAction.Budgets)
        tab("Modules").performClick()
        compose.waitForIdle()
        assertShows("Modules", modules)
    }

    @Test
    fun `Timeline and Search are placeholders`() {
        show()
        tab("Timeline").performClick()
        compose.waitForIdle()
        assertShows("Timeline", "Everything that happened, day by day, from all modules.")
        tab("Search").performClick()
        compose.waitForIdle()
        assertShows("Search", "One search across all modules.")
        back()
        assertShows("Today", today)
    }
}
