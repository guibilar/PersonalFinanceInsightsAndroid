package com.guibilar.financeinsights.home

import android.os.Looper
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
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.HomeSamples
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.home.ui.HomeScreen
import com.guibilar.financeinsights.home.ui.HomeViewModel
import com.guibilar.financeinsights.home.ui.ItemScreen
import com.guibilar.financeinsights.home.ui.ItemViewModel
import com.guibilar.financeinsights.home.ui.NewItemScreen
import com.guibilar.financeinsights.home.ui.NewItemViewModel
import com.guibilar.financeinsights.home.ui.RoomScreen
import com.guibilar.financeinsights.home.ui.RoomViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The Home screens, to look at (build/shots), and what their controls do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeHomeApi()
    private val tasks = HomeTestData.tasks()
    private val documents = HomeTestData.documents()
    private val finance = FakeFinanceApi()
    private val host = FakeHomeHost { HomeTestData.picture() }
    private val today = { HomeSamples.TODAY }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun home(canWrite: Boolean = true, dark: Boolean = false, opened: MutableList<String> = mutableListOf()) {
        val model = HomeViewModel(api, tasks, today)
        show(dark) {
            HomeScreen(
                model,
                PaddingValues(),
                openRoom = { opened += "room $it" },
                openItem = { opened += "item $it" },
                openTask = { opened += "task $it" },
                addItem = { opened += "add" },
                canWrite = canWrite,
            )
        }
    }

    private fun item(id: String = HomeSamples.dishwasher.id, canWrite: Boolean = true, dark: Boolean = false) {
        val model = ItemViewModel(api, documents, finance, tasks, id, host::decodePhoto, decoding = Dispatchers.Unconfined)
        show(dark) { ItemScreen(model, host, PaddingValues(), HomeSamples.TODAY, onBack = {}, canWrite = canWrite) }
    }

    @Test
    fun `the Home screen shows maintenance, the inventory and the rooms`() {
        val opened = mutableListOf<String>()
        home(opened = opened)
        assertShows(
            "Search all items", "Maintenance", "Smoke detector batteries", "Every 12 months", "6 days",
            "Clean the dishwasher filter", "Every month", "22 days", "Inventory · 7 items", "$6,019.96 value",
            "1 warranty ends in the next 30 days", "Kitchen", "2 items · $1,399.99", "No room", "Add room", "Share list for insurance",
        )
        shoot("home")

        compose.onNodeWithText("Kitchen").performClick()
        compose.onNodeWithText("Smoke detector batteries").performClick()
        compose.onNodeWithText("No room").performScrollTo().performClick()
        assertEquals(listOf("room r1", "task ${HomeSamples.BATTERY_TASK}", "room none"), opened)
    }

    @Test
    fun `the Home screen in the dark`() {
        home(dark = true)
        assertShows("Inventory · 7 items")
        shoot("home-dark")
    }

    @Test
    fun `an empty home says how to start`() {
        api.rooms.clear()
        api.items.clear()
        home()
        assertShows("Nothing here yet. Add a room, then the things in it: for example, the dishwasher in the kitchen.", "Add room")
        compose.onAllNodesWithText("Share list for insurance").assertCountEquals(0)
        shoot("home-empty")
    }

    @Test
    fun `a read-only user sees the home and can share the list, but not add`() {
        home(canWrite = false)
        compose.onAllNodesWithContentDescription("Add item").assertCountEquals(0)
        compose.onAllNodesWithText("Add room").assertCountEquals(0)
        assertShows("Share list for insurance")
        shoot("home-read-only")
    }

    @Test
    fun `the search lists the matching items, with their rooms`() {
        val opened = mutableListOf<String>()
        home(opened = opened)
        compose.onNodeWithText("Search all items").performTextInput("lap")
        // The search waits for typing to pause.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(HomeViewModel.SEARCH_DEBOUNCE_MS + 100))
        compose.waitForIdle()
        assertShows("Laptop", "Office · Lenovo ThinkPad X1 · Electronics", "Warranty ended")
        shoot("home-search")

        compose.onNodeWithText("Laptop").performClick()
        assertEquals(listOf("item ${HomeSamples.laptop.id}"), opened)
    }

    @Test
    fun `the warranty line lists the warranties that end soon`() {
        home()
        compose.onNodeWithText("1 warranty ends in the next 30 days").performClick()
        compose.waitForIdle()
        assertShows("Warranties that end in the next 30 days", "Dishwasher", "Warranty ends in 17 days")
        compose.onNodeWithText("Show all").performClick()
        compose.waitForIdle()
        assertShows("Maintenance")
    }

    @Test
    fun `the export downloads the list`() {
        home()
        compose.onNodeWithText("Share list for insurance").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(api.sent.any { it.first == "export" })
    }

    @Test
    fun `a room lists its items`() {
        val opened = mutableListOf<String>()
        val model = RoomViewModel(api, HomeSamples.kitchen.id)
        show { RoomScreen(model, PaddingValues(), HomeSamples.TODAY, openItem = { opened += it }, addItem = { opened += "add" }, onBack = {}) }
        assertShows("Kitchen", "2 items · $1,399.99 value", "Dishwasher", "Bosch SHX78 · Appliance", "Warranty ends in 17 days", "Espresso machine", "$500.00")
        shoot("room")

        compose.onNodeWithText("Espresso machine").performClick()
        assertEquals(listOf(HomeSamples.espresso.id), opened)
    }

    @Test
    fun `an item shows its photo, details, documents, purchase and maintenance`() {
        item()
        assertShows(
            "Dishwasher", "Kitchen", "Warranty ends in 17 days", "$899.99", "FD 9811 04532", "Documents", "Dishwasher photo",
            "Dishwasher warranty", "Warranty · expires Nov 2, 2026", "Add receipt or photo", "Link a document", "Purchase",
            "Link the purchase", "Maintenance", "Clean the dishwasher filter", "Every month · next Oct 25", "Add maintenance",
        )
        compose.onAllNodesWithContentDescription("Photo of Dishwasher").onFirst().assertExists()
        shoot("item")

        compose.onNodeWithText("Dishwasher warranty").performScrollTo().performClick()
        compose.onNodeWithText("Clean the dishwasher filter").performScrollTo().performClick()
        assertEquals(listOf("document ${HomeSamples.WARRANTY_DOCUMENT}", "task ${HomeSamples.FILTER_TASK}"), host.opened)
    }

    @Test
    fun `an item with its purchase, as a read-only user sees it`() {
        item(HomeSamples.laptop.id, canWrite = false, dark = true)
        assertShows("Laptop", "Warranty ended Sep 1, 2026", "Purchase", "METRO 221", "No documents yet.", "No maintenance tasks.")
        compose.onAllNodesWithText("Add receipt or photo").assertCountEquals(0)
        compose.onAllNodesWithText("Link the purchase").assertCountEquals(0)
        compose.onAllNodesWithText("Add maintenance").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Edit details").assertCountEquals(0)
        shoot("item-read-only-dark")

        compose.onNodeWithText("METRO 221").performClick()
        assertEquals(listOf("transaction ${HomeSamples.METRO_TRANSACTION}"), host.opened)
    }

    @Test
    fun `the maintenance sheet adds a monthly task`() {
        item(HomeSamples.sofa.id)
        compose.onNodeWithText("Add maintenance").performScrollTo().performClick()
        compose.waitForIdle()
        assertShows("New maintenance task", "Clean the Sofa", "Every", "Months", "First due")
        compose.onNodeWithText("Add the task").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(tasks.tasks.any { it.title == "Clean the Sofa" && it.repeat != null })
    }

    @Test
    fun `a new item from a purchase fills the form`() {
        val draft = ItemDraft.fromPurchase("Air fryer", -129.99, HomeSamples.TODAY, roomId = HomeSamples.kitchen.id)
        val model = NewItemViewModel(api, documents, draft, transactionId = HomeSamples.METRO_TRANSACTION)
        val created = mutableListOf<String>()
        show { NewItemScreen(model, host, PaddingValues(), onClose = {}, created = { id, _ -> created += id }) }
        assertShows("Add item", "Take a photo", "Pick a photo", "Air fryer", "Kitchen", "129.99", "Oct 3, 2026", "Save")
        shoot("item-new")

        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(api.items.last().id), created)
        assertEquals("Air fryer", api.items.last().name)
    }
}
