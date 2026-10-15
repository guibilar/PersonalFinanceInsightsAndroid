package com.guibilar.financeinsights.journal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeJournalApi
import com.guibilar.financeinsights.JournalSamples
import com.guibilar.financeinsights.journal.ui.JournalScreen
import com.guibilar.financeinsights.journal.ui.JournalViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Journal screen, to look at (build/shots), and what its controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class JournalScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeJournalApi()
    private val today = JournalSamples.TODAY

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun journal(canWrite: Boolean = true, dark: Boolean = false, startDay: String? = null) {
        val model = JournalViewModel(api, startDay) { today }
        show(dark) { JournalScreen(model, PaddingValues(), canWrite = canWrite) }
    }

    @Test
    fun `the journal shows the streak, the week, today, on this day, and the entries`() {
        journal()
        assertShows("6-day streak", "Saturday, October 10", "On this day".uppercase(), "Oct 10, 2025 · 1 year ago", "Entries · 8".uppercase())
        shoot("journal")
    }

    @Test
    fun `the journal in the dark`() {
        journal(dark = true)
        shoot("journal-dark")
    }

    @Test
    fun `writing today saves it`() {
        journal()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("What happened today?").performTextReplacement("Long walk by the river.")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        assertEquals("save" to (today.toString() to "Long walk by the river."), api.sent.last { it.first == "save" })
        assertShows("7-day streak")
    }

    @Test
    fun `a day of the week opens its entry`() {
        journal()
        compose.onNodeWithContentDescription("Thursday, October 8, written").performClick()
        compose.waitForIdle()

        assertShows("Thursday, October 8", "Rain all day. Read half of the new book.", "Back to today", "Delete")
        shoot("journal-earlier-day")
    }

    @Test
    fun `a read-only user reads only`() {
        journal(canWrite = false, startDay = today.minusDays(1).toString())
        assertShows("You can only read the journal.")
        compose.onAllNodesWithText("Save").assertCountEquals(0)
    }
}
