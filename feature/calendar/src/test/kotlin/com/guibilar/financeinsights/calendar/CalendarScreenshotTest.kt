package com.guibilar.financeinsights.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.CalendarSamples
import com.guibilar.financeinsights.FakeCalendarApi
import com.guibilar.financeinsights.calendar.ui.CalendarScreen
import com.guibilar.financeinsights.calendar.ui.CalendarViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Calendar screen, to look at (build/shots), and what its controls do. The tests run in UTC. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class CalendarScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeCalendarApi()
    private val opened = mutableListOf<String>()

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun calendar(
        day: String? = "2026-10-20",
        agenda: Boolean = false,
        canWrite: Boolean = true,
        dark: Boolean = false,
        shared: MutableStateFlow<String?> = MutableStateFlow(null),
    ) {
        val model = CalendarViewModel(api, day, null, agenda) { CalendarSamples.TODAY }
        show(dark) { CalendarScreen(model, PaddingValues(), canWrite = canWrite, onOpenLink = { opened += it }, sharedIcs = shared) }
    }

    @Test
    fun `the month shows the items as dots, and the chosen day's list`() {
        calendar()
        assertShows("October 2026", "Tuesday, October 20", "Change the furnace filter", "Dentist", "3:00 PM – 4:00 PM · Main St 4")
        compose.onNodeWithContentDescription("Tuesday, October 20, 2 items", substring = true).assertExists()
        shoot("calendar")
    }

    @Test
    fun `the month in the dark`() {
        calendar(dark = true)
        shoot("calendar-dark")
    }

    @Test
    fun `the agenda lists the next days`() {
        calendar(day = null, agenda = true)
        assertShows("MONDAY, OCTOBER 19", "Standup", "WEDNESDAY, OCTOBER 21", "Ana's birthday", "All day · Turns 36")
        shoot("calendar-agenda")
    }

    @Test
    fun `another module's item opens its page`() {
        calendar()
        compose.onNodeWithText("Change the furnace filter").performClick()
        compose.waitForIdle()
        assertEquals(listOf("/tasks/t1"), opened)
    }

    @Test
    fun `an event opens its sheet`() {
        calendar()
        compose.onNodeWithText("Dentist").performClick()
        compose.waitForIdle()
        assertShows("Edit event", "Main St 4", "Delete")
        shoot("calendar-event")
    }

    @Test
    fun `an event opened over the new event's sheet shows its own fields`() {
        val model = CalendarViewModel(api, "2026-10-20", null) { CalendarSamples.TODAY }
        show { CalendarScreen(model, PaddingValues()) }
        compose.onNodeWithText("Add event", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // A link's event answering late, while the new event's sheet is open.
        compose.runOnIdle { model.openEvent("ev1", occurrence = null) }
        compose.waitForIdle()
        assertShows("Edit event", "Main St 4")
    }

    @Test
    fun `a new event starts on the chosen day`() {
        calendar()
        compose.onNodeWithText("Add event", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertShows("New event", "Does not repeat")
        shoot("calendar-new-event")
    }

    @Test
    fun `a shared ics file shows its import`() {
        calendar(shared = MutableStateFlow("BEGIN:VCALENDAR"))
        compose.waitForIdle()
        assertShows("Import events", "1 event added.", "Yoga", "Already there")
        shoot("calendar-import")
    }

    @Test
    fun `a read-only user cannot add or import`() {
        calendar(canWrite = false)
        compose.onNodeWithText("Add event", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Import .ics").assertDoesNotExist()
    }
}
