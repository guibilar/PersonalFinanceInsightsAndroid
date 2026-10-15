package com.guibilar.financeinsights.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.FakePeopleApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.HomeSamples
import com.guibilar.financeinsights.PeopleSamples
import com.guibilar.financeinsights.TasksSamples
import com.guibilar.financeinsights.core.people.PersonLink
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.tasks.NewTaskLink
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskRepeat
import com.guibilar.financeinsights.tasks.ui.TaskSheet
import com.guibilar.financeinsights.tasks.ui.TaskViewModel
import com.guibilar.financeinsights.tasks.ui.TasksScreen
import com.guibilar.financeinsights.tasks.ui.TasksViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tasks screen and the task sheet, to look at (build/shots), and what their controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class TasksScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeTasksApi()
    private val documents = FakeDocumentsApi()
    private val finance = FakeFinanceApi()
    private val today = { TasksSamples.TODAY }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    /** The whole screen, with a sheet or a dialog over the page (a window of its own). */
    private fun shootScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun screen(
        canWrite: Boolean = true,
        dark: Boolean = false,
        overdueOnly: Boolean = false,
        opened: MutableList<String> = mutableListOf(),
    ): TasksViewModel {
        val model = TasksViewModel(api, overdueOnly, today)
        show(dark) { TasksScreen(model, PaddingValues(), openTask = { opened += it.id }, canWrite = canWrite) }
        return model
    }

    private fun sheet(id: String, canWrite: Boolean = true, dark: Boolean = false, openedDocuments: MutableList<String> = mutableListOf()) {
        val model = TaskViewModel(api, documents, finance, id)
        show(dark) {
            TaskSheet(
                model,
                TasksSamples.TODAY,
                canWrite = canWrite,
                openDocument = { openedDocuments += it },
                openTransaction = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun `the screen shows the quick add and the sections`() {
        val opened = mutableListOf<String>()
        screen(opened = opened)
        assertShows(
            "Add a task", "Due today", "Overdue · 1", "2 days late · home", "Today · 3", "Compare car insurance quotes", "Documents", "Money",
            "Upcoming · 1", "Thu", "Later and no date · 1", "Recurring", "Every 4 days · next Tue", "Every week · next Tomorrow", "Done · 1",
        )
        shoot("tasks")

        compose.onNodeWithText("Buy printer paper").performClick()
        assertEquals(listOf("t4"), opened)
    }

    @Test
    fun `the screen in the dark, with the done tasks open`() {
        screen(dark = true)
        compose.onNodeWithText("Done · 1").performScrollTo().performClick()
        compose.waitForIdle()
        assertShows("Print session handouts")
        shoot("tasks-dark")
    }

    @Test
    fun `a checkbox marks a task done, and the message offers Undo`() {
        screen()
        // The checkboxes in order: the overdue task, then the three tasks of today ("Buy printer paper" is the last).
        compose.onAllNodes(isToggleable())[3].performClick()
        compose.waitForIdle()
        assertTrue(api.sent.contains("done" to "t4"))
        assertShows("Done: Buy printer paper", "Undo")

        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertTrue(api.sent.contains("undo" to "t4"))
    }

    @Test
    fun `the quick add adds a task on Enter`() {
        screen()
        compose.onNodeWithText("Add a task").performTextInput("Buy milk")
        compose.onNodeWithText("Buy milk").performImeAction()
        compose.waitForIdle()
        assertEquals("Buy milk", api.tasks.last().title)
        assertEquals("2026-10-03", api.tasks.last().dueOn)
        assertShows("Today · 4")
    }

    @Test
    fun `the date chip picks another day`() {
        screen()
        compose.onNodeWithText("Due today").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No date").performClick()
        compose.waitForIdle()
        assertShows("No date")
        compose.onNodeWithText("Add a task").performTextInput("Fix the fence")
        compose.onNodeWithContentDescription("Add the task").performClick()
        compose.waitForIdle()
        assertEquals(null, api.tasks.last().dueOn)
    }

    @Test
    fun `an empty screen says how to start`() {
        api.tasks.clear()
        screen()
        assertShows("No tasks yet. Add one above: for example, water the plants every 4 days.")
        shoot("tasks-empty")
    }

    @Test
    fun `a read-only user sees the tasks, without the quick add`() {
        screen(canWrite = false)
        compose.onAllNodesWithText("Add a task").assertCountEquals(0)
        assertShows("Today · 3")
        shoot("tasks-read-only")
    }

    @Test
    fun `the overdue link shows only the overdue tasks`() {
        screen(overdueOnly = true)
        assertShows("Only the overdue tasks", "Show all", "Call the landlord about the lease")
        compose.onAllNodesWithText("Buy printer paper").assertCountEquals(0)
        shoot("tasks-overdue")

        compose.onNodeWithText("Show all").performClick()
        compose.waitForIdle()
        assertShows("Buy printer paper")
    }

    @Test
    fun `the sheet holds the details, the links and the history`() {
        val openedDocuments = mutableListOf<String>()
        sheet("t3", openedDocuments = openedDocuments)
        assertShows("Title", "Compare car insurance quotes", "Repeat", "Mark done", "Links", "Car insurance policy", "Link to a document", "Last done", "Not done yet.")
        shootScreen("task")

        compose.onNodeWithText("Car insurance policy").performScrollTo().performClick()
        assertEquals(listOf(TasksSamples.INSURANCE_DOCUMENT), openedDocuments)
    }

    @Test
    fun `the sheet of a repeated task shows the repeat form`() {
        sheet("t5")
        assertShows("Every", "Days", "After each done", "On a fixed schedule", "Undo the last done", "Oct 2, 2026 · due Oct 1")
        shootScreen("task-repeat")
    }

    @Test
    fun `a change in the sheet offers Save`() {
        sheet("t8")
        compose.onAllNodesWithText("Save").assertCountEquals(0)
        compose.onNodeWithText("Repeat").performClick()
        compose.waitForIdle()
        assertShows("Save", "After each done")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(api.tasks.first { it.id == "t8" }.repeat != null)
    }

    @Test
    fun `a read-only user sees the task as text`() {
        sheet("t5", canWrite = false, dark = true)
        assertShows("Water the plants", "Every 4 days, after each done", "Links")
        compose.onAllNodesWithText("Mark done").assertCountEquals(0)
        compose.onAllNodesWithText("Link to a document").assertCountEquals(0)
        compose.onAllNodesWithText("Delete the task").assertCountEquals(0)
        shootScreen("task-read-only-dark")
    }

    @Test
    fun `the read-only screen has no working checkbox`() {
        screen(canWrite = false)
        compose.onAllNodesWithText("Buy printer paper").onFirst().assertExists()
        compose.onAllNodes(isToggleable()).onFirst().assertIsNotEnabled()
    }

    @Test
    fun `the sheet shows the items of a task, and links another`() {
        val home = FakeHomeApi()
        api.tasks += TaskItem(
            id = HomeSamples.FILTER_TASK,
            title = "Clean the dishwasher filter",
            dueOn = "2026-10-25",
            repeat = TaskRepeat(1, RepeatUnit.Months, RepeatMode.FromCompletion),
            links = listOf(TaskLink("k20", TaskLinkKinds.HOME_ITEM, HomeSamples.dishwasher.id, "2026-09-20T10:00:00Z")),
        )
        val openedItems = mutableListOf<String>()
        val model = TaskViewModel(api, documents, finance, HomeSamples.FILTER_TASK, home = home)
        show {
            TaskSheet(model, TasksSamples.TODAY, canWrite = true, openDocument = {}, openTransaction = {}, onDismiss = {}, openItem = { openedItems += it })
        }
        assertShows("Dishwasher", "Home · Kitchen · Bosch SHX78 · Appliance", "Link to an item")
        shootScreen("task-items")

        compose.onNodeWithText("Dishwasher").performScrollTo().performClick()
        assertEquals(listOf(HomeSamples.dishwasher.id), openedItems)

        compose.onNodeWithText("Link to an item").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sofa").performClick()
        compose.waitForIdle()
        assertTrue(api.sent.contains("addLink" to NewTaskLink.item(HomeSamples.sofa.id)))
    }

    @Test
    fun `the sheet shows the people of a task, and links another`() {
        api.tasks += TaskItem(id = "t-call", title = "Call Marco", dueOn = "2026-10-16")
        // Marco links to the task from his side; the sheet lists him through the People API (both sides).
        val people = FakePeopleApi(
            listOf(
                PeopleSamples.marco.copy(links = listOf(PersonLink("pl1", PersonLinkKinds.TASK, "t-call"))),
                PeopleSamples.ana,
            ),
        )
        val openedPeople = mutableListOf<String>()
        val model = TaskViewModel(api, documents, finance, "t-call", people = people)
        show {
            TaskSheet(model, TasksSamples.TODAY, canWrite = true, openDocument = {}, openTransaction = {}, onDismiss = {}, openPerson = { openedPeople += it })
        }
        assertShows("Marco Silva", "Link to a person")
        shootScreen("task-people")

        compose.onNodeWithText("Marco Silva").performScrollTo().performClick()
        assertEquals(listOf(PeopleSamples.marco.person.id), openedPeople)

        compose.onNodeWithText("Link to a person").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ana Lima").performClick()
        compose.waitForIdle()
        assertTrue(api.sent.contains("addLink" to NewTaskLink.person(PeopleSamples.ana.person.id)))
    }
}
