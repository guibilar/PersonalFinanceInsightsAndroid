package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.model.Transaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A transaction row with swipes: left to delete (Undo follows), right to change its category. In the deleted list,
 * right restores it. The same actions are offered to TalkBack, for whom swiping isn't easy.
 */
@Composable
fun SwipeRow(
    transaction: Transaction,
    deletedList: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onCategorise: () -> Unit,
    /** The row, given the swipes as TalkBack actions: they must sit on the node TalkBack focuses. */
    content: @Composable (actions: List<CustomAccessibilityAction>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    // A tick when a swipe passes the point where letting go acts.
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val right = if (deletedList) SwipeAction("Restore", Icons.Outlined.RestoreFromTrash, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, onRestore)
    else SwipeAction("Category", Icons.Outlined.Sell, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, onCategorise)
    val left = if (deletedList) null
    else SwipeAction("Delete", Icons.Outlined.Delete, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, onDelete)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled && left != null,
        onDismiss = { value ->
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> right.action()
                SwipeToDismissBoxValue.EndToStart -> left?.action?.invoke()
                SwipeToDismissBoxValue.Settled -> {}
            }
            scope.launch {
                // Deleted (or restored), the row leaves with the reload: snapping back first looked like it failed.
                // It comes back only if it is still here a moment later (the delete failed).
                if (value == SwipeToDismissBoxValue.EndToStart || deletedList) delay(LEAVE_MS)
                state.reset()
            }
        },
        backgroundContent = {
            val action = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> right
                SwipeToDismissBoxValue.EndToStart -> left
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) {
                Row(
                    Modifier.fillMaxSize().background(action.background).padding(horizontal = 24.dp),
                    horizontalArrangement = if (action === right) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(action.icon, null, tint = action.foreground)
                    Spacer(Modifier.width(8.dp))
                    Text(action.label, color = action.foreground, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { content(if (enabled) listOfNotNull(right, left).map { CustomAccessibilityAction(it.label) { it.action(); true } } else emptyList()) }
}

/** How long a swiped-away row waits for the list to drop it before coming back. */
private const val LEAVE_MS = 3_000L

private class SwipeAction(val label: String, val icon: ImageVector, val background: Color, val foreground: Color, val action: () -> Unit)
