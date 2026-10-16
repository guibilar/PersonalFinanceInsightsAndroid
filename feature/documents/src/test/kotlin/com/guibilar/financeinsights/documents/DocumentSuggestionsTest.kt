package com.guibilar.financeinsights.documents

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.documents.DocumentDetails
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentSuggestions
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.documents.TransactionMatch
import com.guibilar.financeinsights.documents.ui.DocumentScreen
import com.guibilar.financeinsights.documents.ui.DocumentViewModel
import com.guibilar.financeinsights.documents.ui.receiptLine
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** T100: "Hestia read this" on a document: link a matching transaction, add one, set the kind and the expiry. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class DocumentSuggestionsTest {
    @get:Rule val compose = createComposeRule()

    /** A scan with its text read, and no transaction yet. */
    private val tax = DocumentsSamples.all.first { it.id == "d4" }.copy(ocrStatus = OcrStatus.Done, kind = DocumentKind.Other)
    private val api = FakeDocumentsApi(DocumentsSamples.all.map { if (it.id == tax.id) tax else it })
    private val added = mutableListOf<List<Any?>>()

    private val read = DocumentSuggestions(
        documentId = tax.id,
        ocrStatus = OcrStatus.Done,
        merchant = "Costco Wholesale",
        date = "2026-10-03",
        total = 33.05,
        kind = DocumentKind.Receipt,
        warrantyMonths = 24,
        warrantyUntil = "2028-10-03",
        transactions = listOf(TransactionMatch("t9", "2026-10-04", "COSTCO WHOLESALE #1234", 33.05, "Visa", 1)),
    )

    private fun show(canWrite: Boolean = true) {
        api.suggestions[tax.id] = read
        val model = DocumentViewModel(api, FakeFinanceApi(), tax.id)
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) {
                Surface {
                    DocumentScreen(
                        model, PaddingValues(), onBack = {}, openTransaction = {}, canWrite = canWrite, today = DocumentsSamples.TODAY,
                        addTransaction = { id, description, amount, date -> added += listOf(id, description, amount, date) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the card says what it read, with the match and the actions`() {
        show()
        compose.onNodeWithText("Costco Wholesale · Oct 3, 2026 · $33.05").assertExists()
        compose.onNodeWithText("COSTCO WHOLESALE #1234").assertExists()
        compose.onNodeWithText("Mark as Receipt").assertExists()
        compose.onNodeWithText("Warranty until Oct 3, 2028: set as expiry").assertExists()
        compose.onRoot().captureRoboImage("build/shots/document-suggestions.png")
    }

    @Test
    fun `Link links the matching transaction`() {
        show()
        compose.onNodeWithText("Link").performClick()
        compose.waitForIdle()
        assertEquals("t9", (api.sent.last { it.first == "addLink" }.second as NewDocumentLink).targetId)
    }

    @Test
    fun `Add as a transaction opens the add sheet with the receipt's values`() {
        show()
        compose.onNodeWithText("Add as a transaction").performScrollTo().performClick()
        assertEquals(listOf(listOf<Any?>(tax.id, "Costco Wholesale", 33.05, "2026-10-03")), added)
    }

    @Test
    fun `the warranty end becomes the expiry, and the other details stay`() {
        show()
        compose.onNodeWithText("Warranty until Oct 3, 2028: set as expiry").performScrollTo().performClick()
        compose.waitForIdle()
        val sent = api.sent.last { it.first == "update" }.second as DocumentDetails
        assertEquals(("2028-10-03" to tax.title), sent.expiresOn to sent.title)
    }

    @Test
    fun `a read-only user sees the line and the match, and no actions`() {
        show(canWrite = false)
        compose.onAllNodesWithText("Costco Wholesale · Oct 3, 2026 · $33.05").onFirst().assertExists()
        compose.onNodeWithText("Link").assertDoesNotExist()
        compose.onNodeWithText("Add as a transaction").assertDoesNotExist()
    }

    @Test
    fun `the line leaves out what is not known`() {
        assertEquals("$33.05", receiptLine(DocumentSuggestions("d", total = 33.05)))
    }
}
