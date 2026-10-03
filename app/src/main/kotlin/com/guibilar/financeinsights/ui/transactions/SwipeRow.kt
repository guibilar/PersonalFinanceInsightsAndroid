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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.model.Transaction
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
    content: @Composable () -> Unit,
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
            // Back in place: a deleted row leaves with the reload, and an edited one stays.
            scope.launch { state.reset() }
        },
        modifier = Modifier.semantics {
            customActions = listOfNotNull(right, left).map { CustomAccessibilityAction(it.label) { it.action(); true } }
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
    ) { content() }
}

private class SwipeAction(val label: String, val icon: ImageVector, val background: Color, val foreground: Color, val action: () -> Unit)
