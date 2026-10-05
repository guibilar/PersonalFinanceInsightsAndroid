package com.guibilar.financeinsights.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.ImportSheet
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ImportSheetTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()

    @Test
    fun `a shared statement over the limit is refused, not uploaded cut short`() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        val big = PickedFile("huge.csv", ByteArray(5 * 1024 * 1024 + 1))
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                Surface {
                    val state by model.state.collectAsState()
                    ImportSheet(state.accounts, model, {}, listOf(big), state.accountsLoaded)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("The file is larger than 5 MB.").assertExists()
        compose.onNodeWithText("Import").assertIsNotEnabled()
        assertTrue(api.sent.none { it.first == "importStatement" })
    }

    private fun showSheet(model: TransactionsViewModel, files: List<PickedFile>, onDismiss: () -> Unit = {}) {
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                Surface {
                    val state by model.state.collectAsState()
                    ImportSheet(state.accounts.filter { it.id == state.accounts.first().id }, model, onDismiss, files, state.accountsLoaded)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `several shared statements are imported one after another, and summed up once all are in`() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        var dismissed = false
        api.importAnswers += ImportResult(read = 1, added = 1, skipped = 0, balance = 900.0, balanceDate = "2026-05-31T00:00:00Z")
        api.importAnswers += ImportResult(read = 4, added = 3, skipped = 1, balance = 1000.0, balanceDate = "2026-06-30T00:00:00Z")
        val messages = mutableListOf<String>()
        showSheet(model, listOf(PickedFile("May.pdf", ByteArray(10)), PickedFile("June.pdf", ByteArray(10)))) { dismissed = true }

        compose.onNodeWithText("May.pdf").assertExists()
        compose.onNodeWithText("Import 2 files").performClick()
        compose.waitForIdle()
        runBlocking { withTimeout(5_000) { messages += model.messages.first().text } }

        assertEquals(2, api.sent.count { it.first == "importStatement" })
        assertTrue(dismissed)
        assertEquals(listOf("Imported 4 new transactions from 2 files (1 already imported); balance on Jun 30, 2026: $1,000.00"), messages)
    }

    @Test
    fun `a refused statement doesn't stop the others, and stays to be tried again`() {
        val model = TransactionsViewModel(api, TransactionsRoute()) { LocalDate.of(2026, 10, 3) }
        var dismissed = false
        api.importAnswers += ApiError.Http(400, "Bad Request", null, null, mapOf("file" to listOf("This PDF can't be read.")))
        api.importAnswers += ImportResult(read = 2, added = 2, skipped = 0)
        showSheet(model, listOf(PickedFile("scan.pdf", ByteArray(10)), PickedFile("June.pdf", ByteArray(10)))) { dismissed = true }

        compose.onNodeWithText("Import 2 files").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Not imported").assertExists()
        compose.onNodeWithText("This PDF can't be read.", substring = true).assertExists()
        compose.onNodeWithText("2 added").assertExists()
        compose.onNodeWithText("Try again").assertIsEnabled()
        compose.onNodeWithText("Close").assertExists()
        assertFalse(dismissed)

        compose.onNodeWithText("Try again").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(3, api.sent.count { it.first == "importStatement" })
        assertTrue(dismissed)
    }
}
