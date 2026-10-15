package com.guibilar.financeinsights.documents

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeHomeApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.HomeSamples
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.documents.ui.DetailsDraft
import com.guibilar.financeinsights.documents.ui.DocumentScreen
import com.guibilar.financeinsights.documents.ui.DocumentViewModel
import com.guibilar.financeinsights.documents.ui.DocumentsListScreen
import com.guibilar.financeinsights.documents.ui.DocumentsListViewModel
import com.guibilar.financeinsights.documents.ui.UploadScreen
import com.guibilar.financeinsights.documents.ui.UploadViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The Documents screens, to look at (build/shots), and what their buttons do. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class DocumentsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeDocumentsApi()
    private val finance = FakeFinanceApi()
    private val today: LocalDate = DocumentsSamples.TODAY

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    /** The whole screen, with a sheet or a dialog over the page (a window of its own). */
    private fun shootScreen(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    private fun list(canWrite: Boolean = true, dark: Boolean = false, opened: MutableList<String> = mutableListOf(), added: MutableList<Boolean> = mutableListOf()) {
        val model = DocumentsListViewModel(api) { today }
        show(dark) { DocumentsListScreen(model, PaddingValues(), open = { opened += it.id }, add = { added += it }, canWrite = canWrite, today = today) }
    }

    private fun detail(
        id: String,
        canWrite: Boolean = true,
        dark: Boolean = false,
        opened: MutableList<String> = mutableListOf(),
        tasks: DocumentTasks? = null,
        items: DocumentItems? = null,
    ) {
        val model = DocumentViewModel(api, finance, id)
        show(dark) {
            DocumentScreen(model, PaddingValues(), onBack = {}, openTransaction = { opened += it }, canWrite = canWrite, today = today, tasks = tasks, items = items)
        }
    }

    /** The Home module as the app joins it (T88), on a fake API; [opened] records the items opened. */
    private fun itemsHost(homeApi: FakeHomeApi, opened: MutableList<String>, write: Boolean = true) = object : DocumentItems {
        override val api: HomeApi = homeApi
        override val canWrite: Boolean = write

        override fun openItem(id: String) {
            opened += id
        }
    }

    /** The Tasks module as the app joins it (T84), on a fake API; [opened] records the tasks opened. */
    private fun tasksHost(tasksApi: FakeTasksApi, opened: MutableList<String>, write: Boolean = true) = object : DocumentTasks {
        override val api: TasksApi = tasksApi
        override val canWrite: Boolean = write

        override fun openTask(id: String) {
            opened += id
        }
    }

    @Test
    fun `the list shows what expires soon, then all documents`() {
        val opened = mutableListOf<String>()
        val added = mutableListOf<Boolean>()
        list(opened = opened, added = added)
        assertShows("Expire soon", "22 days", "30 days", "All documents", "Metro receipt", "Reading text", "Text not read", "Linked")
        shoot("documents")

        compose.onNodeWithText("Apartment lease").performClick()
        compose.onNodeWithContentDescription("Add document").performClick()
        compose.onAllNodesWithContentDescription("Scan a document").onFirst().performClick()
        assertEquals(listOf("d5"), opened)
        assertEquals(listOf(false, true), added)
    }

    @Test
    fun `the list in the dark`() {
        list(dark = true)
        shoot("documents-dark")
    }

    @Test
    fun `a kind chip filters the list`() {
        list()
        compose.onNodeWithText("Warranties").performClick()
        compose.waitForIdle()
        assertShows("1 found", "Dishwasher warranty")
        compose.onAllNodesWithText("Metro receipt").assertCountEquals(0)
        shoot("documents-filtered")
    }

    @Test
    fun `an empty list says how to start`() {
        api.documents.clear()
        list()
        assertShows("No documents yet. Scan a receipt, a warranty or a lease, and find it here again by its text.", "Add a document")
        shoot("documents-empty")
    }

    @Test
    fun `a read-only user sees no add actions`() {
        api.documents.clear()
        list(canWrite = false)
        compose.onAllNodesWithText("Add a document").assertCountEquals(0)
        compose.onAllNodesWithText("Add document").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Scan a document").assertCountEquals(0)
    }

    @Test
    fun `a receipt shows its file, its text and its transaction`() {
        val opened = mutableListOf<String>()
        detail("d3", opened = opened)
        assertShows("Metro receipt", "1 file", "receipt.jpg", "METRO 221", "$41.92", "Link to a transaction")
        shoot("document")

        compose.onAllNodesWithText("METRO 221").onFirst().performClick()
        assertEquals(listOf(DocumentsSamples.METRO_TRANSACTION), opened)
    }

    @Test
    fun `a document whose OCR failed offers to run it again`() {
        detail("d5")
        assertShows("The text could not be read.", "page-2.jpg: the image could not be read.", "Run OCR again", "2 files", "Not linked to a transaction.")
        shoot("document-ocr-failed")
        compose.onNodeWithText("Run OCR again").performClick()
        compose.waitForIdle()
        assertShows("Reading the text of the files…")
        assertEquals("retryOcr", api.sent.last { it.first != "document" }.first)
    }

    @Test
    fun `a document whose OCR runs says so`() {
        detail("d4", dark = true)
        assertShows("Reading the text of the files…")
        shoot("document-ocr-pending-dark")
    }

    @Test
    fun `a read-only user can read a document, but not change it`() {
        detail("d5", canWrite = false)
        compose.onAllNodesWithText("Run OCR again").assertCountEquals(0)
        compose.onAllNodesWithText("Link to a transaction").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Edit details").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        shoot("document-read-only")
    }

    @Test
    fun `the link picker lists the latest transactions`() {
        detail("d1")
        compose.onNodeWithText("Link to a transaction").performClick()
        compose.waitForIdle()
        assertShows("Search descriptions")
        shootScreen("document-link")
    }

    @Test
    fun `the edit sheet holds the details`() {
        detail("d1")
        compose.onAllNodesWithContentDescription("Edit details").onFirst().performClick()
        compose.waitForIdle()
        assertShows("Edit details", "Separate tags with commas.")
        shootScreen("document-edit")
    }

    @Test
    fun `the upload shows the files and the details`() {
        val model = UploadViewModel(api)
        model.add(
            listOf(
                UploadFile("Scan 2026-10-03 14.10.pdf", "%PDF-1.7".toByteArray() + ByteArray(480_000)),
                UploadFile("photo-1.jpg", DocumentsSamples.jpegBytes + ByteArray(1_200_000)),
            ),
        )
        model.edit(DetailsDraft(title = "Kettle receipt", kind = DocumentKind.Receipt, tags = "kitchen", issuedOn = today))
        show { UploadScreen(model, PaddingValues(), onClose = {}, uploaded = {}) }
        assertShows("Scan 2026-10-03 14.10.pdf", "PDF · 469 KB", "photo-1.jpg", "JPG · 1.1 MB", "Upload")
        shoot("document-upload")
    }

    @Test
    fun `an empty upload says what to add`() {
        val model = UploadViewModel(api)
        show { UploadScreen(model, PaddingValues(), onClose = {}, uploaded = {}) }
        assertShows("No files yet. Scan the document, or pick photos or PDFs.", "Scan", "Photos", "Files")
        shoot("document-upload-empty")
    }

    @Test
    fun `a document shows its tasks, and Add task makes the renew task`() {
        val tasksApi = FakeTasksApi()
        val opened = mutableListOf<String>()
        detail("d1", tasks = tasksHost(tasksApi, opened))
        compose.onNodeWithText("Compare car insurance quotes").performScrollTo()
        assertShows("Tasks", "Compare car insurance quotes", "Today", "Add task", "Adds “Renew Car insurance policy”, due 14 days before the expiry.")
        shoot("document-tasks")

        compose.onNodeWithText("Compare car insurance quotes").performClick()
        compose.onNodeWithText("Add task").performScrollTo().performClick()
        compose.waitForIdle()
        val renew = tasksApi.tasks.last()
        assertEquals("Renew Car insurance policy", renew.title)
        assertEquals("2026-10-11", renew.dueOn)
        assertEquals(listOf("d1"), renew.links.map { it.targetId })
        assertEquals(listOf("t3", renew.id), opened)
    }

    @Test
    fun `a document without an expiry date has no Add task, and a read-only user has none`() {
        val tasksApi = FakeTasksApi()
        detail("d3", tasks = tasksHost(tasksApi, mutableListOf()))
        compose.onNodeWithText("No tasks for this document.").performScrollTo()
        compose.onAllNodesWithText("Add task").assertCountEquals(0)
    }

    @Test
    fun `a read-only user sees the tasks of a document, without Add task`() {
        detail("d1", canWrite = false, tasks = tasksHost(FakeTasksApi(), mutableListOf()))
        compose.onNodeWithText("Compare car insurance quotes").performScrollTo()
        compose.onAllNodesWithText("Add task").assertCountEquals(0)
    }

    @Test
    fun `a document shows its items, and Link to item links one`() {
        val opened = mutableListOf<String>()
        detail("d2", items = itemsHost(FakeHomeApi(), opened))
        compose.onNodeWithText("Link to item").performScrollTo()
        assertShows("Items", "Dishwasher", "Kitchen · Bosch SHX78 · Appliance", "$899.99", "Link to item")
        shoot("document-items")

        compose.onNodeWithText("Dishwasher").performClick()
        assertEquals(listOf(HomeSamples.dishwasher.id), opened)

        compose.onNodeWithText("Link to item").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sofa").performClick()
        compose.waitForIdle()
        assertTrue(api.sent.contains("addLink" to NewDocumentLink.item(HomeSamples.sofa.id)))
        assertTrue(api.documents.first { it.id == "d2" }.links.any { it.kind == DocumentLinkKinds.HOME_ITEM && it.targetId == HomeSamples.sofa.id })
    }

    @Test
    fun `a read-only user sees the items of a document, without Link to item`() {
        detail("d2", canWrite = false, items = itemsHost(FakeHomeApi(), mutableListOf()))
        compose.onNodeWithText("Not linked to an item of the home.").assertDoesNotExist()
        assertShows("Dishwasher")
        compose.onAllNodesWithText("Link to item").assertCountEquals(0)
    }
}
