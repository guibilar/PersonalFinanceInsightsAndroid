package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.settings.RulesScreen
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Swiping a transaction, and dragging a rule to a new place. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class GesturesTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()

    private fun transactions() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { TransactionsScreen(model, AccountInfo("dev", "s"), {}, PaddingValues()) } } }
        compose.waitForIdle()
    }

    @Test
    fun `swiping a transaction left deletes it`() {
        transactions()
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(api.sent.any { it.first == "deleteTransaction" })
    }

    @Test
    fun `a swiped-away row whose delete fails comes back`() {
        api.failing += "deleteTransaction"
        transactions()
        val row = hasContentDescription("NO FRILLS #3121", substring = true)
        compose.onNode(row).performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        compose.onNode(row).assertIsDisplayed()
    }

    @Test
    fun `swiping a transaction right opens it to change its category`() {
        transactions()
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Counts as").assertExists()
        assertTrue(api.sent.none { it.first == "deleteTransaction" })
    }

    @Test
    fun `dragging a rule by its handle moves it`() {
        val model = RulesViewModel(api)
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { RulesScreen(model, PaddingValues()) {} } } }
        compose.waitForIdle()
        // METRO, first, dragged down past two rules.
        // The handle itself, not the row its label merges into.
        compose.onNodeWithContentDescription("Drag METRO to reorder", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 240.dp.toPx(), durationMillis = 600)
        }
        compose.waitForIdle()
        val move = api.sent.single { it.first == "moveCategoryRule" }.second as Pair<*, *>
        assertTrue((move.second as Int) >= 2)
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

    @Test
    fun `TalkBack reaches the swipes as actions on the row`() {
        transactions()
        val row = compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true))
        val actions = row.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Category", "Delete"), actions.map { it.label })
        compose.runOnIdle { actions.first { it.label == "Delete" }.action() }
        compose.waitForIdle()
        assertTrue(api.sent.any { it.first == "deleteTransaction" })
    }

    @Test
    fun `pulling down the list refreshes it, with the collapsing bar in the way`() {
        transactions()
        val before = api.transactionFilters.size
        compose.onNodeWithText("Search descriptions and notes").performTouchInput { swipeDown(startY = top, endY = top + 600f, durationMillis = 400) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertTrue(api.transactionFilters.size > before)
    }

    @Test
    fun `the Add button shrinks to its icon while scrolling down, and comes back scrolling up`() {
        transactions()
        compose.onNodeWithText("Add", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Add", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performTouchInput { swipeDown(startY = top, endY = top + 200f) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Add", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a quick sideways flick while scrolling deletes nothing`() {
        transactions()
        // A scroll that starts a little sideways: short, fast, mostly vertical after the first move.
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-60f, 0f), delayMillis = 8)
            moveBy(androidx.compose.ui.geometry.Offset(-40f, -30f), delayMillis = 8)
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue(api.sent.none { it.first == "deleteTransaction" })
    }
}
