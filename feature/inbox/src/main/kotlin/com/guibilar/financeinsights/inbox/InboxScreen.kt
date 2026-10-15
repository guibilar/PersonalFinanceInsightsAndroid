package com.guibilar.financeinsights.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The Inbox (design canvas, "Inbox and quick capture", T18): a quick note that goes to the Inbox, a task, or today's
 * journal; the notes to sort, each with "To task", "To journal", "Done", "Edit", and "Delete"; and the sorted notes,
 * with "Undo". [canWrite] false (a read-only user) shows the notes only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    model: InboxViewModel,
    contentPadding: PaddingValues,
    canWrite: Boolean = true,
    /** The time zone of the times of the notes (the phone's). */
    zone: ZoneId = ZoneId.systemDefault(),
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var editing by remember { mutableStateOf<InboxNote?>(null) }
    var deleting by remember { mutableStateOf<InboxNote?>(null) }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Inbox", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "views") {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("To sort" to false, "Sorted" to true).forEachIndexed { index, (label, sorted) ->
                                    SegmentedButton(
                                        selected = state.sorted == sorted,
                                        onClick = { model.showSorted(sorted) },
                                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                                    ) { Text(label) }
                                }
                            }
                        }
                        if (!state.sorted) {
                            item(key = "quick") {
                                if (canWrite) QuickNote(state, model) else Note("You can only read the Inbox.")
                            }
                        }
                        when (val notes = state.notes) {
                            LoadState.Loading -> item { Skeleton(lines = 3, lineHeight = 48.dp) }
                            is LoadState.Failed -> item { ErrorState(notes.message, model::refresh) }
                            is LoadState.Loaded -> if (notes.value.isEmpty()) {
                                item(key = "empty") {
                                    Note(if (state.sorted) "No sorted notes yet." else "Nothing to sort. A quick note shows here until you sort it.")
                                }
                            } else {
                                item(key = "count") { Note(if (state.total == 1) "1 note" else "${state.total} notes") }
                                items(notes.value, key = { it.id }) { note ->
                                    NoteCard(note, canWrite, state.busy == note.id, zone, model, { editing = it }, { deleting = it })
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    editing?.let { note ->
        var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit the note") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(INBOX_TEXT_LIMIT) }, minLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    editing = null
                    model.edit(note, text)
                }, enabled = text.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete the note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    model.delete(note)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QuickNote(state: InboxState, model: InboxViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.text,
                onValueChange = model::type,
                placeholder = { Placeholder("Type a note, a link, or a task…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Send to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NoteTarget.entries.forEach { target ->
                    FilterChip(selected = state.target == target, onClick = { model.target(target) }, label = { Text(target.label) })
                }
            }
            Button(
                onClick = model::send,
                enabled = state.text.isNotBlank() && state.busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.busy == "new") "Saving…" else "Save") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
    note: InboxNote,
    canWrite: Boolean,
    busy: Boolean,
    zone: ZoneId,
    model: InboxViewModel,
    edit: (InboxNote) -> Unit,
    delete: (InboxNote) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge)
            val time = listOfNotNull(
                noteTime(note.createdAt, zone),
                note.doneAt?.let { "sorted ${note.sortedTo?.let { to -> "to $to " } ?: ""}${noteTime(it, zone) ?: ""}".trim() },
            ).joinToString(" · ")
            if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canWrite) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.doneAt == null) {
                        Button(onClick = { model.sortTo(note, NoteTarget.Task) }, enabled = !busy) { Text("To task") }
                        OutlinedButton(onClick = { model.sortTo(note, NoteTarget.Journal) }, enabled = !busy) { Text("To journal") }
                        OutlinedButton(onClick = { model.done(note) }, enabled = !busy) { Text("Done") }
                        TextButton(onClick = { edit(note) }) { Text("Edit") }
                    } else {
                        OutlinedButton(onClick = { model.reopen(note) }, enabled = !busy) { Text("Undo") }
                    }
                    TextButton(onClick = { delete(note) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Oct 9, 14:00" in [zone]; null when the time cannot be read. */
fun noteTime(at: String?, zone: ZoneId): String? = try {
    at?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)) }
} catch (_: DateTimeParseException) {
    null
}
