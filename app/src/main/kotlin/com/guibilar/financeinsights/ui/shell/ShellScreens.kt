package com.guibilar.financeinsights.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** What + adds (Capture): what each button does. */
class CaptureActions(
    val onTask: () -> Unit,
    val onItem: () -> Unit,
    val onExpense: () -> Unit,
    val onPhoto: () -> Unit,
    val onScan: () -> Unit,
    val onPerson: () -> Unit,
    val onJournal: () -> Unit,
    val onNote: () -> Unit,
)

/**
 * Capture, as a sheet over the page the person is on (the + of the bar): add something in a few taps, and come back to
 * where they were. A read-only user sees the buttons off, and why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(onDismiss: () -> Unit, actions: CaptureActions, canWrite: Boolean = true) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The sheet slides away first, then the action opens its page: no page jumps under a closing sheet.
    fun then(action: () -> Unit): () -> Unit = {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        CaptureContent(
            CaptureActions(
                then(actions.onTask), then(actions.onItem), then(actions.onExpense), then(actions.onPhoto),
                then(actions.onScan), then(actions.onPerson), then(actions.onJournal), then(actions.onNote),
            ),
            canWrite,
        )
    }
}

/** The buttons of Capture (the sheet's content). */
@Composable
fun CaptureContent(actions: CaptureActions, canWrite: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureButton(Icons.Outlined.CheckBox, "Task", enabled = canWrite, Modifier.weight(1f), actions.onTask)
            CaptureButton(Icons.Outlined.Chair, "Item", enabled = canWrite, Modifier.weight(1f), actions.onItem)
            CaptureButton(Icons.Outlined.Payments, "Expense", enabled = canWrite, Modifier.weight(1f), actions.onExpense)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureButton(Icons.Outlined.PhotoCamera, "Photo", enabled = canWrite, Modifier.weight(1f), actions.onPhoto)
            CaptureButton(Icons.Outlined.DocumentScanner, "Scan", enabled = canWrite, Modifier.weight(1f), actions.onScan)
            CaptureButton(Icons.Outlined.Mic, "Voice", enabled = false, Modifier.weight(1f)) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureButton(Icons.Outlined.PersonAdd, "Person", enabled = canWrite, Modifier.weight(1f), actions.onPerson)
            CaptureButton(Icons.Outlined.Book, "Journal", enabled = canWrite, Modifier.weight(1f), actions.onJournal)
            CaptureButton(Icons.Outlined.EditNote, "Note", enabled = canWrite, Modifier.weight(1f), actions.onNote)
        }
        if (!canWrite) {
            Text(
                "This account can only read. It cannot add anything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tall button: an icon over its label. A disabled one is dimmed. */
@Composable
private fun CaptureButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(88.dp).semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
    }
}
