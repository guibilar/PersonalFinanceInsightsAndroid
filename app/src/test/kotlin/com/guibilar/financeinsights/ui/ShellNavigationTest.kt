package com.guibilar.financeinsights.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.FakePeopleApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.FakeTodayApi
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.today.TodayCard
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.TodaySeverity
import com.guibilar.financeinsights.core.today.TodayValue
import com.guibilar.financeinsights.fakeApiClient
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's top-level pages, and how the Money pages open and close: from Modules, from Today, and from outside the
 * app (the shortcut, the widget, the notifications, Capture). Back from a Money page returns to the Money home, and
 * then to the page it was opened from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class ShellNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")
    private val quickAction = MutableStateFlow<QuickAction?>(null)
    private val todayApi = FakeTodayApi()
    private val tasksApi = FakeTasksApi()
    private val homeApi = FakeHomeApi()

    private fun show(canWrite: Boolean = true) {
        val client = fakeApiClient(FakeFinanceApi(), todayApi, FakeDocumentsApi(), FakeAlertsApi(), tasksApi, homeApi, FakePeopleApi())
        compose.setContent {
            FinanceTheme(dynamicColor = false) { Surface { MainScreen(client, account, {}, quickAction = quickAction, canWrite = canWrite) } }
        }
        compose.waitForIdle()
    }

    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertShows(page: String, text: String) = assertTrue("$page shows \"$text\"", shows(text))

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun quick(action: QuickAction) {
        quickAction.value = action
        compose.waitForIdle()
    }

    // What shows only on these pages.
    private val today = "Open Money"
    private val moneyHome = "Overview"
    private val modules = "Spending, budgets and net worth"

    @Test
    fun `the app opens on Today`() {
        show()
        assertShows("Today", today)
        tab("Today").assertIsSelected()
    }

    @Test
    fun `Modules opens Money, and Back returns through the Money home to Modules and Today`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        assertShows("Modules", modules)
        compose.onNodeWithText("Money").performClick()
        compose.waitForIdle()
        assertShows("the Money home", moneyHome)
        compose.onNodeWithText("Transactions").performClick()
        compose.waitForIdle()
        assertShows("the transactions", "Search descriptions and notes")
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Money home", moneyHome)
        back()
        assertShows("Modules", modules)
        back()
        assertShows("Today", today)
    }

    @Test
    fun `a Today tile opens its Money page, and Back returns to the Money home, then Today`() {
        show()
        compose.onAllNodesWithText("Net worth").onFirst().performClick()
        compose.waitForIdle()
        assertShows("net worth", "Net worth")
        assertTrue("not Today", !shows(today))
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Money home", moneyHome)
        back()
        assertShows("Today", today)
    }

    @Test
    fun `each quick action opens its page over the Money home`() {
        show()
        val pages = listOf(
            QuickAction.Dashboard to "Where the month is heading",
            QuickAction.Budgets to "Budgets",
            QuickAction.Subscriptions to "Subscriptions",
            QuickAction.Review to "Review",
            QuickAction.Import to "Import statements",
        )
        pages.forEach { (action, text) ->
            quick(action)
            assertShows("$action", text)
            tab("Modules").assertIsSelected()
            // (A sheet takes Back in its own window; here Back leaves the page with it.)
            back()
            assertShows("the Money home after $action", moneyHome)
            back()
            assertShows("Today after $action", today)
        }
    }

    @Test
    fun `the shortcut opens the Add transaction form`() {
        show()
        quick(QuickAction.AddTransaction)
        assertShows("the add form", "Add a transaction")
    }

    @Test
    fun `Capture's Expense opens the Add transaction form, and Voice is off`() {
        show()
        tab("Capture").performClick()
        compose.waitForIdle()
        listOf("Task", "Item", "Photo", "Scan").forEach { compose.onNodeWithText(it).assertIsEnabled() }
        compose.onNodeWithText("Voice").assertIsNotEnabled()
        compose.onNodeWithText("Expense").performClick()
        compose.waitForIdle()
        assertShows("the add form", "Add a transaction")
    }

    @Test
    fun `a read-only user gets Capture disabled, and says why`() {
        show(canWrite = false)
        tab("Capture").performClick()
        compose.waitForIdle()
        listOf("Task", "Item", "Photo", "Scan", "Voice", "Expense").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        assertShows("Capture", "This account can only read. It cannot add anything.")
    }

    @Test
    fun `a read-only user sees a document without its change actions`() {
        show(canWrite = false)
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Documents").performClick()
        compose.waitForIdle()
        assertTrue("no scan action", compose.onAllNodes(hasContentDescription("Scan a document")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Apartment lease").performClick()
        compose.waitForIdle()
        assertTrue("no OCR retry", !shows("Run OCR again"))
    }

    @Test
    fun `Capture's Photo opens a new document, and Back returns to Capture`() {
        show()
        tab("Capture").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Photo").performClick()
        compose.waitForIdle()
        assertShows("the new document", "Add document")
        back()
        assertShows("Capture", "Expense")
    }

    @Test
    fun `Modules opens Documents and a document, and Back returns through the list to Modules`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Documents").performClick()
        compose.waitForIdle()
        assertShows("the Documents list", "All documents")
        tab("Modules").assertIsSelected()
        compose.onNodeWithText("Apartment lease").performClick()
        compose.waitForIdle()
        assertShows("the document", "Run OCR again")
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Documents list", "All documents")
        back()
        assertShows("Modules", modules)
    }

    @Test
    fun `a Today document item opens the document over the list, and Back returns to the list, then Today`() {
        val item = TodayCard(
            id = "documents.expiring.d1",
            module = "Documents",
            type = TodayCardType.Attention,
            kind = "expiring",
            title = "Document expires soon",
            text = "{title} expires on {day}",
            values = mapOf("title" to TodayValue.plain("Car insurance policy"), "day" to TodayValue.day("2026-10-25")),
            severity = TodaySeverity.Warning,
            link = "/documents/d1",
        )
        todayApi.answer = TodaySamples.complete.copy(cards = TodaySamples.complete.cards + item)
        show()
        compose.onNodeWithText("Car insurance policy expires on Oct 25, 2026").performClick()
        compose.waitForIdle()
        assertShows("the document", "Renew online before the end of the month.")
        tab("Modules").assertIsSelected()
        back()
        assertShows("the Documents list", "All documents")
        back()
        assertShows("Today", today)
    }

    @Test
    fun `Modules again, from a Money page opened on Today, opens the Modules page`() {
        show()
        quick(QuickAction.Budgets)
        tab("Modules").performClick()
        compose.waitForIdle()
        assertShows("Modules", modules)
    }

    @Test
    fun `Timeline and Search are placeholders`() {
        show()
        tab("Timeline").performClick()
        compose.waitForIdle()
        assertShows("Timeline", "Everything that happened, day by day, from all modules.")
        tab("Search").performClick()
        compose.waitForIdle()
        assertShows("Search", "One search across all modules.")
        back()
        assertShows("Today", today)
    }

    /** A Today item that opens METRO 221 (2026-10-03), which has the Metro receipt, then the row's sheet. */
    private fun openMetroSheet() {
        val item = TodayCard(
            id = "money.transaction",
            module = "Money",
            type = TodayCardType.Attention,
            kind = "transaction",
            title = "A transaction",
            text = "Check METRO 221",
            severity = TodaySeverity.Neutral,
            link = "/money/transactions?id=${DocumentsSamples.METRO_TRANSACTION}",
        )
        todayApi.answer = TodaySamples.complete.copy(cards = TodaySamples.complete.cards + item)
        show()
        compose.onNodeWithText("Check METRO 221").performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a transaction's document opens over the transactions, and Back returns there`() {
        openMetroSheet()
        compose.onNodeWithText("Metro receipt").performScrollTo().performClick()
        compose.waitForIdle()
        assertShows("the document", "Link to a transaction")
        tab("Modules").assertIsSelected()
        back()
        assertShows("the transactions", "Transactions")
    }

    @Test
    fun `a transaction's Add document opens the upload for that transaction, and Back returns there`() {
        openMetroSheet()
        compose.onNodeWithText("Add document").performScrollTo().performClick()
        compose.waitForIdle()
        assertShows("the new document", "After the upload, the document is linked to the transaction that you came from.")
        back()
        assertShows("the transactions", "Transactions")
    }

    // Tasks (T84).

    @Test
    fun `Modules opens Tasks, and Back returns to Modules`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tasks").performClick()
        compose.waitForIdle()
        assertShows("Tasks", "Today · 3")
        assertShows("Tasks", "Call the landlord about the lease")
        tab("Modules").assertIsSelected()
        back()
        assertShows("Modules", modules)
    }

    @Test
    fun `Capture's Task opens the quick add, and Back returns to Capture`() {
        show()
        tab("Capture").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Task").performClick()
        compose.waitForIdle()
        assertShows("the quick add", "Add a task")
        assertShows("the quick add", "Due today")
        back()
        assertShows("Capture", "Expense")
    }

    @Test
    fun `a Today overdue task opens the task over the Tasks screen`() {
        val item = TodayCard(
            id = "tasks.overdue.t1",
            module = "Tasks",
            type = TodayCardType.Attention,
            kind = "overdue",
            title = "Task overdue",
            text = "{task} is {days} days late",
            values = mapOf("task" to TodayValue.plain("Call the landlord about the lease"), "days" to TodayValue.count(2)),
            severity = TodaySeverity.Warning,
            link = "/tasks/t1",
        )
        todayApi.answer = TodaySamples.complete.copy(cards = TodaySamples.complete.cards + item)
        show()
        compose.onNodeWithText("Call the landlord about the lease is 2 days late").performClick()
        compose.waitForIdle()
        assertShows("the task sheet", "Mark done")
        assertTrue("the task was read", tasksApi.sent.any { it == "task" to "t1" })
        tab("Modules").assertIsSelected()
    }

    @Test
    fun `a document's task opens over the document`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Documents").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Car insurance policy").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Compare car insurance quotes").performScrollTo().performClick()
        compose.waitForIdle()
        assertShows("the task sheet", "Mark done")
        assertTrue("the task was read", tasksApi.sent.any { it == "task" to "t3" })
    }

    // Home (T88).

    @Test
    fun `Modules opens Home, and Back returns to Modules`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Home").performClick()
        compose.waitForIdle()
        assertShows("Home", "Inventory · 7 items")
        tab("Modules").assertIsSelected()
        compose.onNodeWithText("Kitchen").performClick()
        compose.waitForIdle()
        assertShows("the room", "Espresso machine")
        back()
        back()
        assertShows("Modules", modules)
    }

    @Test
    fun `Capture's Item opens the new item, and Back returns to Capture`() {
        show()
        tab("Capture").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Item").performClick()
        compose.waitForIdle()
        assertShows("the new item", "Add item")
        assertShows("the new item", "Pick a photo")
        back()
        assertShows("Capture", "Expense")
    }

    @Test
    fun `a Today warranty alert opens the item, over the Home screen`() {
        val alert = TodayCard(
            id = "home.warranty-ending.i1",
            module = "Home",
            type = TodayCardType.Attention,
            kind = "warranty-ending",
            title = "Warranty ends soon",
            text = "The warranty of {item} ends on {date}",
            values = mapOf("item" to TodayValue.plain("Dishwasher"), "date" to TodayValue.plain("Oct 20")),
            severity = TodaySeverity.Neutral,
            link = "/home/items/i1",
        )
        todayApi.answer = TodaySamples.complete.copy(cards = TodaySamples.complete.cards + alert)
        show()
        compose.onNodeWithText("The warranty of Dishwasher ends on Oct 20").performClick()
        compose.waitForIdle()
        assertShows("the item", "FD 9811 04532")
        assertTrue("the item was read", homeApi.sent.any { it == "item" to "i1" })
        tab("Modules").assertIsSelected()
        back()
        assertShows("Home", "Inventory · 7 items")
    }
}
