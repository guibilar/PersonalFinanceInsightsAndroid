package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.transactions.LINK_CANDIDATES
import com.guibilar.financeinsights.ui.transactions.TransactionDocumentsModel
import com.guibilar.financeinsights.ui.transactions.documentSubtitle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The documents of one transaction (T14): the list from the link, the search, and the link. */
class TransactionDocumentsTest {
    private val api = FakeDocumentsApi()
    private val metro = DocumentsSamples.METRO_TRANSACTION

    private fun model(transactionId: String = metro) = TransactionDocumentsModel(api, transactionId)

    private fun titles(state: LoadState<List<DocumentSummary>>?) =
        state?.valueOrNull?.map { it.title }

    @Test
    fun `lists the documents linked to the transaction`() = runTest {
        val model = model()
        model.load()
        assertEquals(listOf("Metro receipt"), titles(model.documents))
        assertEquals(DocumentLinkKinds.MONEY_TRANSACTION to metro, api.sent.last().second)
        assertEquals("Receipt · 1 file", documentSubtitle(model.documents.valueOrNull!!.single()))
    }

    @Test
    fun `a transaction with no documents gets an empty list`() = runTest {
        val model = model("01a0ff22-0000-7000-8000-000000000000")
        model.load()
        assertEquals(emptyList<String>(), titles(model.documents))
    }

    @Test
    fun `a failed load says so, and Retry loads again`() = runTest {
        api.failing += "byLink"
        val model = model()
        model.load()
        assertTrue(model.documents is LoadState.Failed)

        api.failing.clear()
        model.retry()
        assertEquals(listOf("Metro receipt"), titles(model.documents))
    }

    @Test
    fun `a failed reload keeps the list`() = runTest {
        val model = model()
        model.load()
        api.failing += "byLink"
        model.load()
        assertEquals(listOf("Metro receipt"), titles(model.documents))
    }

    @Test
    fun `the search lists the newest documents, or the matches`() = runTest {
        val model = model()
        model.search("  ")
        assertEquals(DocumentsSamples.all.take(LINK_CANDIDATES).map { it.title }, titles(model.candidates))
        assertEquals(listOf(null, null, null, null, null, 1, LINK_CANDIDATES), api.sent.last().second)

        model.search(" lease ")
        assertEquals(listOf("Apartment lease"), titles(model.candidates))
    }

    @Test
    fun `links a document, then lists it`() = runTest {
        val model = model()
        model.load()
        assertTrue(model.link(DocumentsSamples.dishwasher.summary))
        assertEquals(
            "addLink" to NewDocumentLink.transaction(metro),
            api.sent.first { it.first == "addLink" },
        )
        assertEquals(setOf("d2", "d3"), model.linkedIds)
        assertNull(model.error)
        assertFalse(model.busy)
    }

    @Test
    fun `a link that exists already counts as made`() = runTest {
        api.refusing["addLink"] = ApiError.Http(409, "Conflict", "The document has this link already.", null, emptyMap())
        val model = model()
        model.load()
        assertTrue(model.link(DocumentsSamples.receipt.summary))
        assertNull(model.error)
        // The list was loaded again.
        assertEquals(2, api.sent.count { it.first == "byLink" })
    }

    @Test
    fun `a refused write marks the user as read-only`() = runTest {
        api.refusing["addLink"] = FakeDocumentsApi.readOnly
        val model = model()
        model.load()
        assertFalse(model.link(DocumentsSamples.dishwasher.summary))
        assertTrue(model.readOnly)
        assertNull(model.error)
    }

    @Test
    fun `a failed link says why`() = runTest {
        api.refusing["addLink"] = ApiError.Http(400, "Bad Request", null, null, mapOf("targetId" to listOf("No finance transaction has this id.")))
        val model = model()
        model.load()
        assertFalse(model.link(DocumentsSamples.dishwasher.summary))
        assertEquals("No finance transaction has this id.", model.error)

        api.refusing.clear()
        api.failing += "addLink"
        assertFalse(model.link(DocumentsSamples.dishwasher.summary))
        assertEquals("Can't reach the server. Check your connection and that Tailscale is on.", model.error)
        model.clearError()
        assertNull(model.error)
    }
}
