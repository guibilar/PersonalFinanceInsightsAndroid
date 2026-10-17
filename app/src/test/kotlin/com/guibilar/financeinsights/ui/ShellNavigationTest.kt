package com.guibilar.financeinsights.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import com.guibilar.financeinsights.search.SearchApi
import com.guibilar.financeinsights.search.SearchResult
import com.guibilar.financeinsights.timeline.TimelineApi
import com.guibilar.financeinsights.timeline.TimelineDay
import com.guibilar.financeinsights.timeline.TimelineEntry
import com.guibilar.financeinsights.timeline.TimelinePage
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

    /** The Timeline (T16): one journal entry today. */
    private val timelineApi = object : TimelineApi {
        val asked = mutableListOf<String?>()

        override suspend fun page(before: String?, days: Int, module: String?): TimelinePage {
            asked += before
            return TimelinePage(
                today = "2026-10-03", from = "2026-09-20", to = "2026-10-03", earlier = "2026-09-20",
                days = listOf(
                    TimelineDay(
                        "2026-10-03",
                        listOf(TimelineEntry("journal.entry.e1", "Journal", "entry", "2026-10-03", null, "Journal", "Long walk by the river.", null, "/nowhere")),
                    ),
                ),
            )
        }
    }

    /** The Inbox (T101): empty; a shared text starts a note. */
    private val inboxApi = object : com.guibilar.financeinsights.inbox.InboxApi {
        override suspend fun notes(done: Boolean, pageSize: Int) = com.guibilar.financeinsights.inbox.InboxPage()
        override suspend fun add(text: com.guibilar.financeinsights.inbox.InboxText) = com.guibilar.financeinsights.inbox.InboxNote("n1", text.text)
        override suspend fun update(id: String, text: com.guibilar.financeinsights.inbox.InboxText) = com.guibilar.financeinsights.inbox.InboxNote(id, text.text)
        override suspend fun done(id: String, done: com.guibilar.financeinsights.inbox.InboxDone) = com.guibilar.financeinsights.inbox.InboxNote(id)
        override suspend fun reopen(id: String) = com.guibilar.financeinsights.inbox.InboxNote(id)
        override suspend fun delete(id: String) {}
    }

    /** Search (T17): nothing is searched in these tests. */
    private val searchApi = object : SearchApi {
        override suspend fun search(q: String, module: String?) = SearchResult(q)
    }

    private fun show(canWrite: Boolean = true) {
        val client = fakeApiClient(FakeFinanceApi(), todayApi, FakeDocumentsApi(), FakeAlertsApi(), tasksApi, homeApi, FakePeopleApi(), timelineApi, searchApi, inboxApi, com.guibilar.financeinsights.FakeJournalApi(), com.guibilar.financeinsights.FakeCalendarApi())
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) { Surface { MainScreen(client, account, {}, quickAction = quickAction, canWrite = canWrite) } }
        }
        compose.waitForIdle()
    }

    private fun shows(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertShows(page: String, text: String) = assertTrue("$page shows \"$text\"", shows(text))

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    /** A focused page (one item, a form): the bar has stepped aside. */
    private fun assertNoBar() =
        assertTrue("no bar", compose.onAllNodes(hasText("Modules") and isSelectable()).fetchSemanticsNodes().isEmpty())

    /** A button of the + sheet (Capture). */
    private fun add(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun openAddSheet() {
        tab("Add").performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun quick(action: QuickAction) {
        quickAction.value = action
        compose.waitForIdle()
    }

    // What shows only on these pages.
    private val today = "Your day"
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
        // T106: the tiles are below "Your day": scroll to the tile first.
        compose.onAllNodesWithText("Net worth").onFirst().performScrollTo().performClick()
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
    fun `the + sheet's Expense opens the Add transaction form, and Voice is off`() {
        show()
        openAddSheet()
        listOf("Task", "Item", "Photo", "Scan").forEach { add(it).assertIsEnabled() }
        add("Voice").assertIsNotEnabled()
        add("Expense").performClick()
        compose.waitForIdle()
        assertShows("the add form", "Add a transaction")
    }

    @Test
    fun `a read-only user gets the + sheet disabled, and says why`() {
        show(canWrite = false)
        openAddSheet()
        listOf("Task", "Item", "Photo", "Scan", "Voice", "Expense").forEach { add(it).assertIsNotEnabled() }
        assertShows("the sheet", "This account can only read. It cannot add anything.")
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
    fun `the + sheet's Photo opens a new document, and Back returns to the page it was opened on`() {
        show()
        openAddSheet()
        add("Photo").performClick()
        compose.waitForIdle()
        assertShows("the new document", "Add document")
        back()
        assertShows("Today", today)
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
        assertNoBar()
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
        assertNoBar()
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
    fun `Timeline (under Modules) shows the entries, and Search asks for a text`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Timeline").performScrollTo().performClick()
        compose.waitForIdle()
        tab("Modules").assertIsSelected()
        assertShows("Timeline", "Long walk by the river.")
        assertTrue(timelineApi.asked == listOf<String?>(null))
        tab("Search").performClick()
        compose.waitForIdle()
        assertShows("Search", "Type at least 2 characters. The search looks at transactions, documents (with their text), tasks, home items, people and the journal.")
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
        assertNoBar()
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
    fun `the + sheet's Task opens the quick add, and Back returns to the page it was opened on`() {
        show()
        openAddSheet()
        add("Task").performClick()
        compose.waitForIdle()
        assertShows("the quick add", "Add a task")
        assertShows("the quick add", "Due today")
        back()
        assertShows("Today", today)
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
    fun `the + sheet's Item opens the new item, and Back returns to the page it was opened on`() {
        show()
        openAddSheet()
        add("Item").performClick()
        compose.waitForIdle()
        assertShows("the new item", "Add item")
        assertShows("the new item", "Pick a photo")
        back()
        assertShows("Today", today)
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
        assertNoBar()
        back()
        assertShows("Home", "Inventory · 7 items")
    }

    // ---- T101: Share to Hestia ----

    private fun share(share: com.guibilar.financeinsights.Share) {
        com.guibilar.financeinsights.Shares.pending.value = share
        compose.waitForIdle()
    }

    @Test
    fun `shared text starts a note in the Inbox`() {
        show()
        share(com.guibilar.financeinsights.Share.Text("Recipe: https://example.com/soup"))
        assertShows("the Inbox", "Recipe: https://example.com/soup")
        assertTrue(com.guibilar.financeinsights.Shares.pending.value == null)
    }

    @Test
    fun `a shared vcf opens the import of contacts with its preview`() {
        show()
        share(com.guibilar.financeinsights.Share.Contacts("BEGIN:VCARD\nFN:Dan Wu\nEND:VCARD"))
        assertShows("the import", "Import contacts")
        assertShows("the preview", "Dan Wu")
    }

    @Test
    fun `a shared PDF asks if it is a document or a statement`() {
        show()
        val pdf = com.guibilar.financeinsights.core.documents.UploadFile("bill.pdf", "%PDF-1.4".toByteArray())
        share(com.guibilar.financeinsights.Share.Pdfs(listOf(pdf), listOf(com.guibilar.financeinsights.ui.transactions.PickedFile("bill.pdf", pdf.bytes))))
        assertShows("the question", "Keep or import this PDF?")
        compose.onNodeWithText("Keep as a document").performClick()
        compose.waitForIdle()
        assertShows("the new document", "bill.pdf")
    }


    @Test
    fun `the bar steps aside on a document, and comes back with the list`() {
        show()
        tab("Modules").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Documents").performClick()
        compose.waitForIdle()
        tab("Modules").assertExists()
        compose.onNodeWithText("Apartment lease").performClick()
        compose.waitForIdle()
        assertShows("the document", "Run OCR again")
        assertNoBar()
        back()
        tab("Modules").assertIsSelected()
    }

    @Test
    fun `Calendar is a tab, and Back from it returns to Today`() {
        show()
        tab("Calendar").performClick()
        compose.waitForIdle()
        tab("Calendar").assertIsSelected()
        back()
        assertShows("Today", today)
    }
}
