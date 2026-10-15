package com.guibilar.financeinsights.people

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakePeopleApi
import com.guibilar.financeinsights.PeopleSamples
import com.guibilar.financeinsights.core.people.PersonLink
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.people.PersonLinkedFrom
import com.guibilar.financeinsights.people.ui.PeopleScreen
import com.guibilar.financeinsights.people.ui.PeopleViewModel
import com.guibilar.financeinsights.people.ui.PersonFormScreen
import com.guibilar.financeinsights.people.ui.PersonFormViewModel
import com.guibilar.financeinsights.people.ui.PersonScreen
import com.guibilar.financeinsights.people.ui.PersonViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The People screens, to look at (build/shots), and what their controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class PeopleScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakePeopleApi()
    private val today = { PeopleSamples.TODAY }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun people(canWrite: Boolean = true, dark: Boolean = false, opened: MutableList<String> = mutableListOf()) {
        val model = PeopleViewModel(api, today)
        show(dark) { PeopleScreen(model, PaddingValues(), openPerson = { opened += it }, addPerson = { opened += "add" }, canWrite = canWrite) }
    }

    private fun person(canWrite: Boolean = true, dark: Boolean = false, opened: MutableList<String> = mutableListOf()) {
        val model = PersonViewModel(api, PeopleSamples.MARCO, today)
        show(dark) {
            PersonScreen(
                model,
                PaddingValues(),
                openLink = { kind, id -> opened += "$kind $id" },
                edit = { opened += "edit" },
                deleted = { opened += "deleted" },
                onBack = {},
                canWrite = canWrite,
            )
        }
    }

    @Test
    fun `the People screen shows reach out, upcoming, loans and everybody`() {
        val opened = mutableListOf<String>()
        people(opened = opened)
        assertShows(
            "Find a person", "REACH OUT", "Marco Silva", "Last contact 7 weeks ago", "UPCOMING", "Marco Silva’s birthday",
            "In 19 days · turns 36 · 2 gift ideas", "LENT AND BORROWED", "Camping stove", "Lent to Marco Silva · due Aug 1, 2026",
            "ALL PEOPLE · 2", "All", "Family", "Gaming group", "Ana Lima", "2 days ago",
        )
        shoot("people")

        compose.onNodeWithText("Ana Lima").performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("Add person").onFirst().performClick()
        assertEquals(listOf(PeopleSamples.ANA, "add"), opened)
    }

    @Test
    fun `the People screen in the dark`() {
        people(dark = true)
        assertShows("ALL PEOPLE · 2")
        shoot("people-dark")
    }

    @Test
    fun `nobody yet says how to start, and a read-only user cannot add`() {
        api.people.clear()
        people(canWrite = false)
        assertShows("No people yet.")
        compose.onAllNodesWithContentDescription("Add person").assertCountEquals(0)
        shoot("people-empty-read-only")
    }

    @Test
    fun `a person shows the strip, gift ideas, the open loan, and the history`() {
        api.people[0] = PeopleSamples.marco.copy(links = listOf(PersonLink("l1", PersonLinkKinds.HOME_ITEM, "i9")))
        val opened = mutableListOf<String>()
        person(opened = opened)
        assertShows(
            "Marco Silva", "Friend · Gaming group", "Log contact", "Gift idea", "Lend item", "Birthday", "Oct 22, 1990",
            "Last contact", "Aug 15", "Reminder", "Every 6 weeks", "GIFT IDEAS", "Pour-over coffee kettle", "Birthday · $49.50",
            "Board game", "Given Dec 25, 2025 · $64.00", "LENT AND BORROWED", "Got it back", "HISTORY", "Aug 15, 2026 · Coffee",
            "Talked about his new job.", "Jul 4, 2026 · Lent",
        )
        shoot("person")

        compose.onNodeWithText("Home item").performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("Edit").onFirst().performClick()
        assertEquals(listOf("${PersonLinkKinds.HOME_ITEM} i9", "edit"), opened)
    }

    @Test
    fun `a person lists the documents and tasks that link to them, without Unlink`() {
        api.people[0] = PeopleSamples.marco.copy(
            linkedFrom = listOf(
                PersonLinkedFrom(PersonLinkKinds.DOCUMENT, "d1", "Birthday card scan"),
                PersonLinkedFrom(PersonLinkKinds.TASK, "t-call", "Call Marco"),
            ),
        )
        val opened = mutableListOf<String>()
        person(opened = opened)
        compose.onNodeWithText("Call Marco").performScrollTo()
        assertShows("Birthday card scan", "Document links here", "Call Marco", "Task links here")
        compose.onAllNodesWithText("Unlink").assertCountEquals(0)

        compose.onNodeWithText("Call Marco").performClick()
        assertEquals(listOf("${PersonLinkKinds.TASK} t-call"), opened)
    }

    @Test
    fun `log contact opens a dialog`() {
        person()
        compose.onNodeWithText("Log contact").performClick()
        compose.waitForIdle()
        assertShows("Kind", "Call", "Day (empty: today)", "Note", "Cancel")
        shoot("person-log-contact")
    }

    @Test
    fun `a read-only user sees the person without actions`() {
        person(canWrite = false)
        compose.onAllNodesWithText("Log contact").assertCountEquals(0)
        compose.onAllNodesWithText("Got it back").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Edit").assertCountEquals(0)
        shoot("person-read-only")
    }

    @Test
    fun `the person form`() {
        val model = PersonFormViewModel(api, PeopleSamples.MARCO, today)
        show { PersonFormScreen(model, PaddingValues(), onClose = {}, saved = {}) }
        assertShows("Edit person", "Marco Silva", "Friend", "Gaming group", "October", "22", "1990", "Every 6 weeks", "Save")
        shoot("person-form")
    }
}
