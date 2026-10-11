package com.guibilar.financeinsights.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Something to tell the person after an action, with an optional action of its own ("Undo"). */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/**
 * Shows each message as a snackbar; one with an action stays longer. A newer message replaces the one showing:
 * queued, deleting a few rows in a row made each "Undo" wait its turn for many seconds.
 */
@Composable
fun ShowMessages(messages: Flow<UiMessage>, snackbar: SnackbarHostState) {
    LaunchedEffect(messages) {
        messages.collectLatest { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = message.actionLabel,
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }
}
