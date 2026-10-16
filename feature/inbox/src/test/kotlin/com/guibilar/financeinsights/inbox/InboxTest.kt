package com.guibilar.financeinsights.inbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeJournalApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/** The Inbox screen, to look at (build/shots), and what its controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class InboxTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeInboxApi()
    private val tasks = FakeTasksApi()
    private val journal = FakeJournalApi()

    private fun show(canWrite: Boolean = true, dark: Boolean = false) {
        val model = InboxViewModel(api, NoteSender(tasks, journal))
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) {
                Surface { InboxScreen(model, PaddingValues(), canWrite = canWrite, zone = ZoneId.of("America/Toronto")) }
            }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    @Test
    fun `the inbox shows the quick note and the notes to sort`() {
        show()
        assertShows("Quick note", "Send to", "2 notes", "Call the plumber\nAbout the leak under the sink", "Oct 10, 10:22")
        shoot("inbox")
    }

    @Test
    fun `the inbox in the dark`() {
        show(dark = true)
        shoot("inbox-dark")
    }

    @Test
    fun `a quick note goes to the Inbox, or to a task, or to today's journal`() {
        show()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("  Buy furnace filters ")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("add" to "Buy furnace filters", api.sent.last { it.first == "add" })
        assertShows("3 notes", "Saved to the Inbox")

        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Fix the gate\nThe latch is loose")
        compose.onNodeWithText("Task").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        val task = tasks.sent.last { it.first == "create" }.second
        assertTrue(task.toString(), task.toString().contains("title=Fix the gate") && task.toString().contains("notes=The latch is loose"))

        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("A long walk.")
        compose.onNodeWithText("Journal").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("save" to (FakeJournalApi().today.toString() to "A long walk."), journal.sent.last { it.first == "save" })
    }

    @Test
    fun `a note goes to a task, then is sorted, and the sorted view has an undo`() {
        show()
        compose.onAllNodesWithText("To task")[0].performClick()
        compose.waitForIdle()
        assertEquals("done" to ("n1" to "Task"), api.sent.last { it.first == "done" })
        assertTrue(tasks.sent.any { it.first == "create" })
        assertShows("1 note")

        compose.onNodeWithText("Sorted").performClick()
        compose.waitForIdle()
        assertShows("Call the plumber\nAbout the leak under the sink", "Undo")
        shoot("inbox-sorted")
        compose.onAllNodesWithText("Undo")[0].performClick()
        compose.waitForIdle()
        assertNull(api.notes.first { it.id == "n1" }.doneAt)
    }

    @Test
    fun `a read-only user reads only`() {
        show(canWrite = false)
        assertShows("You can only read the Inbox.", "2 notes")
        compose.onAllNodesWithText("To task").assertCountEquals(0)
    }

    @Test
    fun `task and journal texts from a note`() {
        assertEquals("Title" to "rest", taskFromNote("  Title \n rest  ").let { it.title to it.notes })
        assertNull(taskFromNote("Only a title").notes)
        assertEquals(200, taskFromNote("x".repeat(250)).title.length)
        assertEquals("Old\n\nNew", journalWithNote("Old", " New "))
        assertEquals("New", journalWithNote(null, "New"))
    }

    @Test
    fun `an email note shows its sender, and opens its attached document (T102)`() {
        val email = InboxNote("n9", "Your October bill\n\nAmount due: $84.12", "2026-10-09T18:00:00Z", source = "Email", from = "Hydro <bills@hydro.example>", documentIds = listOf("d1"))
        val opened = mutableListOf<String>()
        val model = InboxViewModel(FakeInboxApi(listOf(email)), NoteSender(tasks, journal))
        compose.setContent {
            com.guibilar.financeinsights.ui.theme.FinanceTheme(ripples = false, dynamicColor = false) {
                Surface { InboxScreen(model, PaddingValues(), zone = ZoneId.of("America/Toronto"), openDocument = { opened += it }) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Email from Hydro <bills@hydro.example>").assertExists()
        compose.onNodeWithText("Attached document").performClick()
        assertEquals(listOf("d1"), opened)
    }
}
