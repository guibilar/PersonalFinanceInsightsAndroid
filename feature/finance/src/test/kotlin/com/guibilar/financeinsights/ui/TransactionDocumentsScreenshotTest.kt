package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FinanceDocuments
import com.guibilar.financeinsights.FinanceHome
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionDocumentsContent
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** A transaction's "Documents" section (T14): with and without documents, and in the edit sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class TransactionDocumentsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val finance = FakeFinanceApi()
    private val documentsApi = FakeDocumentsApi()
    private val today = LocalDate.of(2026, 10, 3)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")
    private val metro = DocumentsSamples.METRO_TRANSACTION

    /** The Documents pages as the app gives them, recording what they were asked to open. */
    private inner class FakeDocuments(override val canWrite: Boolean = true) : FinanceDocuments {
        override val api: DocumentsApi = documentsApi
        val opened = mutableListOf<String>()
        val added = mutableListOf<String>()

        override fun openDocument(id: String) {
            opened += id
        }

        override fun addDocument(transactionId: String) {
            added += transactionId
        }
    }

    private fun section(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface(Modifier.padding(0.dp)) { content() } }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun withDocuments(dark: Boolean) = section(dark) {
        TransactionDocumentsContent(
            documents = LoadState.Loaded(listOf(DocumentsSamples.receipt.summary, DocumentsSamples.dishwasher.summary)),
            canWrite = true,
            open = {},
            add = {},
            link = {},
            retry = {},
            modifier = Modifier.padding(20.dp),
        )
    }

    @Test
    fun withDocuments() {
        withDocuments(dark = false)
        compose.onNodeWithText("Metro receipt").assertExists()
        compose.onNodeWithText("Receipt · 1 file").assertExists()
        compose.onNodeWithText("Add document").assertExists()
        shoot("transaction-documents")
    }

    @Test
    fun withDocumentsDark() {
        withDocuments(dark = true)
        shoot("transaction-documents-dark")
    }

    @Test
    fun withoutDocuments() {
        section {
            TransactionDocumentsContent(
                documents = LoadState.Loaded(emptyList()),
                canWrite = true,
                open = {},
                add = {},
                link = {},
                retry = {},
                modifier = Modifier.padding(20.dp),
            )
        }
        compose.onNodeWithText("No documents yet. Add the receipt, for example.").assertExists()
        shoot("transaction-documents-empty")
    }

    @Test
    fun readOnly() {
        section {
            TransactionDocumentsContent(
                documents = LoadState.Loaded(listOf(DocumentsSamples.receipt.summary)),
                canWrite = false,
                readOnly = true,
                open = {},
                add = {},
                link = {},
                retry = {},
                modifier = Modifier.padding(20.dp),
            )
        }
        compose.onNodeWithText("Add document").assertDoesNotExist()
        compose.onNodeWithText("Link a document").assertDoesNotExist()
        compose.onNodeWithText("You can only read documents.").assertExists()
        shoot("transaction-documents-read-only")
    }

    @Test
    fun failedLoad() {
        section {
            TransactionDocumentsContent(
                documents = LoadState.Failed("Could not load the documents."),
                canWrite = true,
                open = {},
                add = {},
                link = {},
                retry = {},
                modifier = Modifier.padding(20.dp),
            )
        }
        compose.onNodeWithText("Retry").assertExists()
        shoot("transaction-documents-failed")
    }

    /** The edit sheet of METRO 221, which has the Metro receipt. */
    private fun openMetro(documents: FakeDocuments) {
        val model = TransactionsViewModel(finance, TransactionsRoute(transactionId = metro)) { today }
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) {
                Surface { TransactionsScreen(model, account, {}, PaddingValues(), documents = documents) }
            }
        }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun inTheEditSheet() {
        val documents = FakeDocuments()
        openMetro(documents)
        compose.onNodeWithText("Documents").performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/transaction-documents-sheet.png")

        compose.onNodeWithText("Metro receipt").performClick()
        assertEquals(listOf("d3"), documents.opened)
        compose.onNodeWithText("Add document").performScrollTo().performClick()
        assertEquals(listOf(metro), documents.added)
    }

    @Test
    fun linkADocument() {
        openMetro(FakeDocuments())
        compose.onNodeWithText("Link a document").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Recent documents").performScrollTo().assertExists()
        captureScreenRoboImage("build/shots/transaction-documents-link.png")

        compose.onNodeWithContentDescription("Link Dishwasher warranty").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Recent documents").assertDoesNotExist()
        compose.onNodeWithText("Dishwasher warranty").performScrollTo().assertExists()
    }

    @Test
    fun noSectionWithoutTheDocumentsModule() {
        val model = TransactionsViewModel(finance, TransactionsRoute(transactionId = metro)) { today }
        compose.setContent { FinanceTheme(ripples = false, dynamicColor = false) { Surface { TransactionsScreen(model, account, {}, PaddingValues()) } } }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete transaction").performScrollTo()
        compose.onNodeWithText("Link a document").assertDoesNotExist()
    }

    @Test
    fun addToInventory() {
        val added = mutableListOf<String>()
        val home = object : FinanceHome {
            override fun addToInventory(transaction: Transaction) {
                added += transaction.id
            }
        }
        val model = TransactionsViewModel(finance, TransactionsRoute(transactionId = metro)) { today }
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) { Surface { TransactionsScreen(model, account, {}, PaddingValues(), home = home) } }
        }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add to inventory").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(metro), added)
    }

    @Test
    fun noAddToInventoryForAReadOnlyUser() {
        val home = object : FinanceHome {
            override val canWrite = false

            override fun addToInventory(transaction: Transaction) {}
        }
        val model = TransactionsViewModel(finance, TransactionsRoute(transactionId = metro)) { today }
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) { Surface { TransactionsScreen(model, account, {}, PaddingValues(), home = home) } }
        }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete transaction").performScrollTo()
        compose.onNodeWithText("Add to inventory").assertDoesNotExist()
    }
}
