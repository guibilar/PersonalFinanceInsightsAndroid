package com.guibilar.financeinsights.search

import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The Search screen, to look at (build/shots), and what its controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class SearchTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeSearchApi()
    private val opened = mutableListOf<String>()

    private fun show(dark: Boolean = false) {
        val model = SearchViewModel(api)
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) {
                Surface { SearchScreen(model, PaddingValues(), open = { opened += it }) }
            }
        }
        compose.waitForIdle()
    }

    private fun type(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        pause()
    }

    /** The pause before the search: it runs on the main looper, which Robolectric holds until told. */
    private fun pause() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    @Test
    fun `the search shows the best hits of each module`() {
        show()
        assertShows("Type at least 2 characters. The search looks at transactions, documents (with their text), tasks, home items, people and the journal.")
        type("  dishwasher ")

        assertEquals(listOf("dishwasher" to null), api.sent)
        assertShows(
            "10 results in 4 modules", "People could not search.",
            "TRANSACTIONS", "Home Depot", "Sep 8, 2025 · Household · CIBC Visa", "-$899.00",
            "DOCUMENTS", "Warranty card", "TASKS", "Show all 7", "ITEMS", "Dishwasher",
        )
        shoot("search")
    }

    @Test
    fun `the search in the dark`() {
        show(dark = true)
        type("dishwasher")
        shoot("search-dark")
    }

    @Test
    fun `show all of one module, back to all, and a hit opens its page`() {
        show()
        type("dishwasher")
        compose.onNodeWithText("Show all 7").performClick()
        compose.waitForIdle()
        assertEquals("dishwasher" to "Tasks", api.sent.last())
        assertShows("Only Tasks.", "Dishwasher job 7")

        compose.onNodeWithText("Search all modules").performClick()
        compose.waitForIdle()
        assertEquals("dishwasher" to null, api.sent.last())

        compose.onNodeWithText("Home Depot").performClick()
        assertEquals(listOf("/money/transactions?id=t1"), opened)
    }

    @Test
    fun `nothing found, and a failed search offers a retry`() {
        show()
        type("zzz")
        assertShows("No results for “zzz”.")

        api.failing = true
        type("dishwasher")
        assertShows("Can't reach the server. Check your connection and that Tailscale is on.")
        api.failing = false
        compose.onNodeWithText("Try again").performClick()
        pause()
        assertShows("10 results in 4 modules")
    }
}
