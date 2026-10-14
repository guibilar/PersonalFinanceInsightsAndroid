package com.guibilar.financeinsights.documents

import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.documents.ui.DetailsDraft
import com.guibilar.financeinsights.documents.ui.DocumentViewModel
import com.guibilar.financeinsights.documents.ui.DocumentsListViewModel
import com.guibilar.financeinsights.documents.ui.LinkedTransaction
import com.guibilar.financeinsights.documents.ui.UploadViewModel
import com.guibilar.financeinsights.ui.common.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeDocumentsApi()
    private val finance = FakeFinanceApi()
    private val today = { DocumentsSamples.TODAY }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val pdf = UploadFile("scan.pdf", "%PDF-1.7".toByteArray())

    // The list.

    @Test
    fun `the list shows the documents and, without a filter, the ones that expire in the next 30 days`() = runTest(dispatcher) {
        val model = DocumentsListViewModel(api, today)
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(DocumentsSamples.all.map { it.id }, state.items.map { it.id })
        // The soonest first: the policy (Oct 25), then the warranty (Nov 2). The lease (Jan 31) is later.
        assertEquals(listOf("d1", "d2"), state.expiring.map { it.id })
        assertTrue(api.sent.any { it.first == "documents" && it.second == listOf(null, null, null, "2026-10-03", "2026-11-02", 1, 20) })
    }

    @Test
    fun `a kind and a search filter the list, and hide the expiring section`() = runTest(dispatcher) {
        val model = DocumentsListViewModel(api, today)
        advanceUntilIdle()

        model.setKind(DocumentKind.Warranty)
        advanceUntilIdle()
        assertEquals(listOf("d2"), model.state.value.items.map { it.id })
        assertTrue(model.state.value.expiring.isEmpty())

        model.setKind(null)
        model.search("policy")
        advanceTimeBy(DocumentsListViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertEquals("policy", model.state.value.query)
        assertEquals(listOf("d1"), model.state.value.items.map { it.id })

        model.clearAll()
        advanceUntilIdle()
        assertEquals(5, model.state.value.items.size)
        assertEquals(2, model.state.value.expiring.size)
    }

    @Test
    fun `the list loads more pages as it scrolls`() = runTest(dispatcher) {
        api.documents.clear()
        repeat(25) { index -> api.documents += DocumentsSamples.receipt.copy(id = "r$index", links = emptyList()) }
        val model = DocumentsListViewModel(api, today)
        advanceUntilIdle()
        assertEquals(20, model.state.value.items.size)

        model.loadMore()
        advanceUntilIdle()
        assertEquals(25, model.state.value.items.size)
        assertTrue(model.state.value.endReached)
    }

    @Test
    fun `a failed list says why, and a quiet reload keeps the list`() = runTest(dispatcher) {
        api.failing += "documents"
        val model = DocumentsListViewModel(api, today)
        advanceUntilIdle()
        assertEquals("Can't reach the server. Check your connection and that Tailscale is on.", model.state.value.error)

        api.failing.clear()
        model.refresh()
        advanceUntilIdle()
        assertNull(model.state.value.error)

        model.resumed()
        api.failing += "documents"
        model.resumed()
        advanceUntilIdle()
        assertEquals(5, model.state.value.items.size)
        assertNull(model.state.value.error)
    }

    // One document.

    @Test
    fun `a document shows its linked transactions from finance`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d3")
        advanceUntilIdle()

        val linked = model.state.value.transactions[DocumentsSamples.METRO_TRANSACTION]
        assertTrue(linked is LinkedTransaction.Found)
        assertEquals("METRO 221", (linked as LinkedTransaction.Found).transaction.description)
        assertEquals(DocumentsSamples.METRO_TRANSACTION, finance.transactionFilters.first().transactionId)
    }

    @Test
    fun `a transaction that finance does not know is said so`() = runTest(dispatcher) {
        val gone = "01a0ff22-0000-7000-8000-000000000000"
        api.documents.replaceAll { if (it.id == "d3") it.copy(links = it.links.map { l -> l.copy(targetId = gone) }) else it }
        val model = DocumentViewModel(api, finance, "d3")
        advanceUntilIdle()

        assertEquals(LinkedTransaction.Missing, model.state.value.transactions[gone])
        // The live ones first, then the deleted ones.
        assertEquals(listOf(null, true), finance.transactionFilters.map { it.hidden })
    }

    @Test
    fun `a deleted transaction is found among the deleted ones`() = runTest(dispatcher) {
        finance.deletedIds += DocumentsSamples.METRO_TRANSACTION
        val model = DocumentViewModel(api, finance, "d3")
        advanceUntilIdle()

        val linked = model.state.value.transactions[DocumentsSamples.METRO_TRANSACTION] as LinkedTransaction.Found
        assertTrue(linked.deleted)
    }

    @Test
    fun `a link that is not a GUID is unknown, with no call to finance`() = runTest(dispatcher) {
        api.documents.replaceAll { if (it.id == "d3") it.copy(links = it.links.map { l -> l.copy(targetId = "gone") }) else it }
        val model = DocumentViewModel(api, finance, "d3")
        advanceUntilIdle()

        assertEquals(LinkedTransaction.Missing, model.state.value.transactions["gone"])
        assertTrue(finance.transactionFilters.isEmpty())
    }

    @Test
    fun `while the OCR is pending, the document is read every 3 seconds until it is done`() = runTest(dispatcher) {
        api.ocrDoneAfter = 3
        val model = DocumentViewModel(api, finance, "d4")
        runCurrent()
        assertEquals(OcrStatus.Pending, model.state.value.document.valueOrNull?.ocrStatus)

        advanceTimeBy(DocumentViewModel.POLL_EVERY_MS + 1)
        assertEquals(2, api.reads["d4"])
        assertEquals(OcrStatus.Pending, model.state.value.document.valueOrNull?.ocrStatus)

        advanceTimeBy(DocumentViewModel.POLL_EVERY_MS)
        assertEquals(3, api.reads["d4"])
        assertEquals(OcrStatus.Done, model.state.value.document.valueOrNull?.ocrStatus)
        assertEquals("Text found in T4 slip 2025", model.state.value.document.valueOrNull?.ocrText)

        advanceUntilIdle()
        assertEquals(3, api.reads["d4"])
    }

    @Test
    fun `the polling stops after 2 minutes and says to come back later`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d4")
        advanceUntilIdle()

        assertTrue(model.state.value.ocrSlow)
        // The first read, then one every 3 seconds for 2 minutes.
        assertEquals(1 + 40, api.reads["d4"])
    }

    @Test
    fun `run OCR again sets the document back to pending and polls`() = runTest(dispatcher) {
        api.ocrDoneAfter = 2
        val model = DocumentViewModel(api, finance, "d5")
        advanceUntilIdle()
        assertEquals(OcrStatus.Failed, model.state.value.document.valueOrNull?.ocrStatus)

        model.retryOcr()
        runCurrent()
        assertEquals(OcrStatus.Pending, model.state.value.document.valueOrNull?.ocrStatus)
        advanceUntilIdle()
        assertEquals(OcrStatus.Done, model.state.value.document.valueOrNull?.ocrStatus)
    }

    @Test
    fun `saving sends all the details`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d1")
        advanceUntilIdle()
        val before = DataChanges.version

        val draft = DetailsDraft.of(model.document!!).copy(title = "Car policy", tags = "car, Renewal", expiresOn = null)
        val error = model.save(draft.toDetails())

        assertNull(error)
        val saved = model.state.value.document.valueOrNull!!
        assertEquals("Car policy", saved.title)
        assertEquals(listOf("car", "renewal"), saved.tags)
        assertNull(saved.expiresOn)
        assertEquals("2025-10-25", saved.issuedOn)
        assertEquals(DocumentKind.Insurance, saved.kind)
        assertTrue(DataChanges.version > before)
    }

    @Test
    fun `a refused save says why`() = runTest(dispatcher) {
        api.refusing["update"] = ApiError.Http(400, "Invalid", null, null, mapOf("title" to listOf("Give a title.")))
        val model = DocumentViewModel(api, finance, "d1")
        advanceUntilIdle()

        assertEquals("Give a title.", model.save(DetailsDraft(title = " ").toDetails()))
    }

    @Test
    fun `link and unlink a transaction`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d1")
        advanceUntilIdle()
        model.findCandidates()
        advanceUntilIdle()
        val candidates = (model.state.value.candidates as LoadState.Loaded).value
        assertTrue(candidates.isNotEmpty())

        model.link(candidates.first())
        advanceUntilIdle()
        val link = model.document!!.transactionLinks.single()
        assertEquals(candidates.first().id, link.targetId)
        assertTrue(model.state.value.transactions[link.targetId] is LinkedTransaction.Found)

        model.unlink(link)
        advanceUntilIdle()
        assertTrue(model.document!!.links.isEmpty())
        assertTrue(api.documents.first { it.id == "d1" }.links.isEmpty())
    }

    @Test
    fun `delete ends the document`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d2")
        advanceUntilIdle()

        model.delete()
        advanceUntilIdle()

        assertTrue(model.state.value.deleted)
        assertFalse(api.documents.any { it.id == "d2" })
    }

    // The upload.

    @Test
    fun `the upload sends the files and the details, then gives the new document`() = runTest(dispatcher) {
        val model = UploadViewModel(api)
        model.add(listOf(pdf))
        model.edit(DetailsDraft(title = "Kettle", kind = DocumentKind.Warranty, tags = "kitchen, Kettle", expiresOn = LocalDate.of(2028, 10, 1)))
        val before = DataChanges.version

        model.upload()
        advanceUntilIdle()

        assertEquals(
            listOf("files" to "scan.pdf", "title" to "Kettle", "kind" to "Warranty", "tags" to "kitchen", "tags" to "kettle", "expiresOn" to "2028-10-01"),
            api.uploads.single(),
        )
        assertEquals("Kettle", model.state.value.uploaded?.title)
        assertTrue(DataChanges.version > before)
    }

    @Test
    fun `files the server would refuse are said so before the upload`() = runTest(dispatcher) {
        val model = UploadViewModel(api)
        model.add(listOf(pdf, UploadFile("notes.txt", "hello".toByteArray())), unreadable = 1)
        assertEquals(
            "Could not read one of the files. Only PDF, JPEG, PNG, HEIC and WebP files can go: notes.txt.",
            model.state.value.error,
        )

        model.upload()
        advanceUntilIdle()
        assertTrue(api.uploads.isEmpty())

        model.remove(model.state.value.files.last())
        assertNull(model.state.value.error)
    }

    @Test
    fun `a too large upload refused by the server is said so`() = runTest(dispatcher) {
        api.refusing["upload"] = ApiError.Http(413, null, null, null, emptyMap())
        val model = UploadViewModel(api)
        model.add(listOf(pdf))

        model.upload()
        advanceUntilIdle()

        assertEquals("The server refused the upload as too large. Try fewer files, or smaller ones.", model.state.value.error)
        assertNull(model.state.value.uploaded)
    }

    // The upload from a transaction (T14).

    @Test
    fun `an upload from a transaction starts as a receipt, and links the new document to it`() = runTest(dispatcher) {
        val transaction = DocumentsSamples.METRO_TRANSACTION
        val model = UploadViewModel(api, linkTo = transaction)
        assertEquals(DocumentKind.Receipt, model.state.value.draft.kind)
        model.add(listOf(pdf))

        model.upload()
        advanceUntilIdle()

        val state = model.state.value
        val document = state.uploaded!!
        assertTrue(state.linked)
        assertNull(state.linkError)
        assertEquals("Receipt", api.uploads.single().first { it.first == "kind" }.second)
        assertEquals("addLink" to NewDocumentLink.transaction(transaction), api.sent.last())
        assertEquals(listOf(transaction), api.documents.first { it.id == document.id }.links.map { it.targetId })
    }

    @Test
    fun `a link that exists already counts as made`() = runTest(dispatcher) {
        api.refusing["addLink"] = ApiError.Http(409, "Conflict", "The document has this link already.", null, emptyMap())
        val model = UploadViewModel(api, linkTo = DocumentsSamples.METRO_TRANSACTION)
        model.add(listOf(pdf))

        model.upload()
        advanceUntilIdle()

        assertTrue(model.state.value.linked)
    }

    @Test
    fun `a failed link keeps the new document, and says to link it on its page`() = runTest(dispatcher) {
        api.failing += "addLink"
        val model = UploadViewModel(api, linkTo = DocumentsSamples.METRO_TRANSACTION)
        model.add(listOf(pdf))
        model.edit(model.state.value.draft.copy(title = "Metro"))

        model.upload()
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.linked)
        assertEquals("Added “Metro”, but could not link it to the transaction. Link it here.", state.linkError)
        assertEquals("Metro", state.uploaded?.title)
        assertFalse(state.uploading)
    }

    @Test
    fun `an upload without a transaction does not link`() = runTest(dispatcher) {
        val model = UploadViewModel(api)
        model.add(listOf(pdf))
        model.upload()
        advanceUntilIdle()

        assertFalse(model.state.value.linked)
        assertTrue(api.sent.none { it.first == "addLink" })
    }

    @Test
    fun `a document page opened with a notice shows it once`() = runTest(dispatcher) {
        val model = DocumentViewModel(api, finance, "d3", notice = "Added “Metro”, but could not link it.")
        advanceUntilIdle()
        assertEquals("Added “Metro”, but could not link it.", model.messages.first().text)
    }
}
