package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** A link to one transaction (T56): the highlighted row, and the messages when the list cannot show it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1200dp-xxhdpi")
class TransactionLinkScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    /** METRO 221, on 2026-10-03. */
    private val metro = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"

    private fun show(id: String, dark: Boolean = false): TransactionsViewModel {
        val model = TransactionsViewModel(api, TransactionsRoute(transactionId = id)) { today }
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { TransactionsScreen(model, account, {}, PaddingValues()) } }
        }
        return model
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun highlighted() = highlighted(dark = false)

    @Test
    fun highlightedDark() = highlighted(dark = true)

    private fun highlighted(dark: Boolean) {
        // The clock stops, so the picture shows the highlight at full strength (it fades after 1.5 s).
        compose.mainClock.autoAdvance = false
        val model = show(metro, dark)
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(metro, model.state.value.highlighted)
        shoot(if (dark) "transaction-link-highlighted-dark" else "transaction-link-highlighted")

        // After its time, the highlight goes.
        compose.mainClock.advanceTimeBy(3_500)
        compose.waitForIdle()
        assertNull(model.state.value.highlighted)
    }

    @Test
    fun deleted() {
        api.deletedIds += metro
        val model = show(metro)
        compose.waitForIdle()
        compose.onNodeWithText("The linked transaction is deleted.").assertIsDisplayed()
        shoot("transaction-link-deleted")

        compose.onNodeWithText("Show deleted transactions").performClick()
        compose.waitForIdle()
        assertEquals(true, model.state.value.showDeleted)
        assertNull(model.state.value.linkNotice)
    }

    @Test
    fun missing() {
        val model = show("01a0ff22-0000-7000-8000-000000000000")
        compose.waitForIdle()
        compose.onNodeWithText("The linked transaction does not exist.").assertIsDisplayed()
        shoot("transaction-link-missing")

        compose.onNode(androidx.compose.ui.test.hasContentDescription("Close the message")).performClick()
        compose.waitForIdle()
        assertNull(model.state.value.linkNotice)
    }
}
