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
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.money.MoneyHomeScreen
import com.guibilar.financeinsights.ui.money.attention
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The Money module's home: the four tiles, and a row for each finance page that opens it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class MoneyHomeTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun money() {
        show { MoneyHomeScreen(DashboardViewModel(api) { today }, PaddingValues(), onBack = {}, open = {}, openImport = {}) }
        shoot("money")
    }

    @Test
    fun moneyDark() {
        show(dark = true) { MoneyHomeScreen(DashboardViewModel(api) { today }, PaddingValues(), onBack = {}, open = {}, openImport = {}) }
        shoot("money-dark")
    }

    @Test
    fun `each row opens its page`() {
        val opened = mutableListOf<Any>()
        show { MoneyHomeScreen(DashboardViewModel(api) { today }, PaddingValues(), onBack = {}, open = { opened += it }, openImport = {}) }
        listOf("Dashboard", "Review", "Transactions", "Subscriptions", "Accounts", "Categories", "Tags", "Rules").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        assertEquals(
            listOf(DashboardRoute, ReviewRoute(), TransactionsRoute(), SubscriptionsRoute, AccountsRoute, CategoriesRoute, TagsRoute, RulesRoute),
            opened,
        )
    }

    @Test
    fun `the dashboard row says how much needs attention`() {
        val model = DashboardViewModel(api) { today }
        show { MoneyHomeScreen(model, PaddingValues(), onBack = {}, open = {}, openImport = {}) }
        val count = model.state.value.attention().size
        assertTrue("the recorded data has something that needs attention", count > 0)
        compose.onNodeWithText(if (count == 1) "1 item needs attention" else "$count items need attention").assertExists()
    }

    @Test
    fun `import opens the transactions with the import`() {
        var imports = 0
        show { MoneyHomeScreen(DashboardViewModel(api) { today }, PaddingValues(), onBack = {}, open = {}, openImport = { imports++ }) }
        compose.onNode(hasContentDescription("Import statements")).performClick()
        assertEquals(1, imports)
    }
}
