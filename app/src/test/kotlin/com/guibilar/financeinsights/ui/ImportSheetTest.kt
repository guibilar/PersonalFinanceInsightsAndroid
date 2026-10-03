package com.guibilar.financeinsights.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.ImportSheet
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
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
                    ImportSheet(state.accounts, model, {}, big, state.accountsLoaded)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("The file is larger than 5 MB.").assertExists()
        compose.onNodeWithText("Import").assertIsNotEnabled()
        assertTrue(api.sent.none { it.first == "importStatement" })
    }
}
