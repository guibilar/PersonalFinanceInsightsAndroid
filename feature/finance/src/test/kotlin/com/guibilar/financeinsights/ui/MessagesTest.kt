package com.guibilar.financeinsights.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Messages after an action, as snackbars. */
@RunWith(RobolectricTestRunner::class)
class MessagesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a new message replaces the one showing, so its Undo is there at once`() {
        val messages = Channel<UiMessage>(Channel.BUFFERED)
        val flow = messages.receiveAsFlow()
        compose.setContent {
            FinanceTheme(ripples = false, dynamicColor = false) {
                val snackbar = remember { SnackbarHostState() }
                ShowMessages(flow, snackbar)
                SnackbarHost(snackbar)
            }
        }
        messages.trySend(UiMessage("Deleted “COFFEE”", "Undo") {})
        compose.waitForIdle()
        compose.onNodeWithText("Deleted “COFFEE”").assertIsDisplayed()

        messages.trySend(UiMessage("Deleted “GROCERIES”", "Undo") {})
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Deleted “GROCERIES”").assertIsDisplayed()
        compose.onNodeWithText("Deleted “COFFEE”").assertDoesNotExist()
    }
}
