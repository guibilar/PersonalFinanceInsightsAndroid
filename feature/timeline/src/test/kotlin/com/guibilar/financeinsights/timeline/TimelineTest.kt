package com.guibilar.financeinsights.timeline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/** The Timeline screen, to look at (build/shots), and what its controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class TimelineTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeTimelineApi()
    private val opened = mutableListOf<String>()
    private val toronto = ZoneId.of("America/Toronto")

    private fun show(dark: Boolean = false) {
        val model = TimelineViewModel(api)
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) {
                Surface { TimelineScreen(model, PaddingValues(), open = { opened += it }, zone = toronto) }
            }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    @Test
    fun `the timeline shows the days, newest first, with their entries`() {
        show()
        assertEquals(listOf(Triple<String?, Int, String?>(null, 14, null)), api.sent)
        assertShows(
            "TODAY · SAT OCT 10", "Costco", "-$142.18", "Groceries · Neo card",
            "YESTERDAY · FRI OCT 9", "16:15 · Insurance", "11:40 · Done · repeats",
            "THU OCT 1", "September in review",
        )
        shoot("timeline")
    }

    @Test
    fun `the timeline in the dark`() {
        show(dark = true)
        shoot("timeline-dark")
    }

    @Test
    fun `an entry opens its page, a chip filters, and earlier days load`() {
        show()
        compose.onNodeWithText("Costco").performClick()
        assertEquals(listOf("/money/transactions?id=t1"), opened)

        compose.onNodeWithText("Money").performClick()
        compose.waitForIdle()
        assertEquals("Money", api.sent.last().third)
        compose.onAllNodesWithText("Journal").assertCountEquals(1) // the chip only

        compose.onNodeWithText("Show earlier").performScrollTo().performClick()
        compose.waitForIdle()
        // The next page ends on Sep 25, the latest earlier day with an entry (the host skips the empty days).
        assertEquals(Triple<String?, Int, String?>("2026-09-26", 14, "Money"), api.sent.last())
        assertShows("FRI SEP 25", "Payroll", "$2,400.00")
        // Nothing earlier: no "Show earlier" (it went on through empty weeks for ever).
        compose.onNodeWithText("Show earlier").assertDoesNotExist()
        compose.onNodeWithText("That is everything: nothing happened earlier.").performScrollTo().assertExists()
    }

    @Test
    fun `a failed module is named, and a failed page offers a retry`() {
        api.failedModules = listOf("Money")
        show()
        assertShows("Money could not load. The other modules show.")

        api.failing = true
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        assertShows("Can't reach the server. Check your connection and that Tailscale is on.")
        shoot("timeline-failed")
    }

    @Test
    fun `day titles and times`() {
        assertEquals("Today · Sat Oct 10", dayHeading("2026-10-10", "2026-10-10"))
        assertEquals("Yesterday · Fri Oct 9", dayHeading("2026-10-09", "2026-10-10"))
        assertEquals("Wed Dec 31, 2025", dayHeading("2025-12-31", "2026-10-10"))
        assertEquals("22:30", entryTime("2026-10-10T02:30:00+00:00", toronto))
        assertEquals(null, entryTime(null, toronto))
    }
}
