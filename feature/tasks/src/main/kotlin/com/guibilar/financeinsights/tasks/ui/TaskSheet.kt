package com.guibilar.financeinsights.tasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskDraft
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.dueText
import com.guibilar.financeinsights.core.tasks.repeatLongText
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One task in a sheet over the Tasks screen: its details (title, notes, due date, tags, repeat), done or undo, its
 * links (a tap opens the document or the transaction), the last completions, and delete. [canWrite] false (a read-only
 * user) shows the details as text, with no write actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    model: TaskViewModel,
    today: LocalDate,
    canWrite: Boolean,
    openDocument: (String) -> Unit,
    openTransaction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LaunchedEffect(state.deleted) { if (state.deleted) onDismiss() }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var unlinking by rememberSaveable { mutableStateOf<String?>(null) }
    val task = state.task.valueOrNull

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val loaded = state.task) {
                LoadState.Loading -> Skeleton(lines = 8, lineHeight = 24.dp)
                is LoadState.Failed -> ErrorState(loaded.message, model::reload)
                is LoadState.Loaded -> {
                    val item = loaded.value
                    if (canWrite) {
                        TaskForm(item, model::save)
                        DoneRow(item, state.busy, model::done, model::undo)
                    } else {
                        TaskFacts(item, today)
                    }
                    HorizontalDivider()
                    LinksBlock(
                        links = item.links,
                        state = state,
                        canWrite = canWrite,
                        openDocument = openDocument,
                        openTransaction = openTransaction,
                        unlink = { unlinking = it.id },
                        pick = model::openPicker,
                    )
                    HorizontalDivider()
                    CompletionsBlock(item)
                    if (canWrite) {
                        TextButton(onClick = { deleting = true }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete the task", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            SnackbarHost(snackbar)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (deleting && task != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete the task?") },
            text = { Text("“${task.title}”, its history and its links will be deleted. Linked documents and transactions stay.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    model.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    unlinking?.let { linkId ->
        val link = task?.links?.firstOrNull { it.id == linkId }
        if (link == null) {
            unlinking = null
        } else {
            AlertDialog(
                onDismissRequest = { unlinking = null },
                title = { Text("Remove the link?") },
                text = { Text("The task and the linked item both stay. Only the link goes.") },
                confirmButton = {
                    TextButton(onClick = {
                        unlinking = null
                        model.unlink(link)
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Cancel") } },
            )
        }
    }
    when (state.picker) {
        LinkPicker.Document -> PickerSheet(
            title = "Link to a document",
            searchHint = "Search documents",
            candidates = state.documentCandidates,
            empty = { search -> if (search.isBlank()) "No documents yet." else "No document matches “${search.trim()}”." },
            search = model::search,
            onDismiss = model::closePicker,
        ) { document: DocumentSummary ->
            val linked = task?.links.orEmpty().any { it.kind == TaskLinkKinds.DOCUMENT && it.targetId == document.id }
            CandidateRow(document.title, listOfNotNull(document.kind.label, if (linked) "Linked" else null).joinToString(" · "), null, enabled = !linked) {
                model.closePicker()
                model.linkDocument(document)
            }
        }
        LinkPicker.Transaction -> PickerSheet(
            title = "Link to a transaction",
            searchHint = "Search descriptions",
            candidates = state.transactionCandidates,
            empty = { search -> if (search.isBlank()) "No transactions yet." else "No transactions match “${search.trim()}”." },
            search = model::search,
            onDismiss = model::closePicker,
        ) { transaction: Transaction ->
            val linked = task?.links.orEmpty().any { it.kind == TaskLinkKinds.TRANSACTION && it.targetId == transaction.id }
            CandidateRow(
                transaction.description,
                listOfNotNull(formatDay(transaction.date), transaction.accountName.ifBlank { null }, if (linked) "Linked" else null).joinToString(" · "),
                Money.format(transaction.amount),
                enabled = !linked,
            ) {
                model.closePicker()
                model.linkTransaction(transaction)
            }
        }
        null -> {}
    }
}

/** The details as a form; Save sends all of them. */
@Composable
private fun TaskForm(task: TaskItem, save: suspend (TaskDraft) -> String?) {
    val original = remember(task) { TaskDraft.of(task) }
    var draft by rememberSaveable(task.id, task.updatedAt, stateSaver = TaskDraftSaver) { mutableStateOf(original) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            label = { Text("Title") },
            supportingText = draft.titleError?.let { { Text(it) } },
            isError = draft.titleError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.dueOn?.let { formatDay(it) } ?: if (draft.repeats) "Today" else "No date",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Due") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { picking = true }) { Icon(Icons.Outlined.CalendarMonth, "Pick the due date") }
            if (draft.dueOn != null) IconButton(onClick = { draft = draft.copy(dueOn = null) }) { Icon(Icons.Outlined.Close, "Clear the due date") }
        }
        OutlinedTextField(
            value = draft.tags,
            onValueChange = { draft = draft.copy(tags = it) },
            label = { Text("Tags") },
            placeholder = { Text("home, garden", maxLines = 1) },
            supportingText = { Text("Separate tags with commas.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { draft = draft.copy(notes = it) },
            label = { Text("Notes") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        RepeatForm(draft) { draft = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (draft != original) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = {
                    draft = original
                    error = null
                }) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            error = save(draft)
                            saving = false
                        }
                    },
                    enabled = !saving && draft.canSave,
                ) { Text("Save") }
            }
        }
    }
    if (picking) {
        PickDayDialog(draft.dueOn ?: LocalDate.now(), onPick = { draft = draft.copy(dueOn = it) }, onDismiss = { picking = false })
    }
}

/** The repeat: on or off; every N units; and from the due date or from each done, in plain words. */
@Composable
private fun RepeatForm(draft: TaskDraft, onChange: (TaskDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(draft.repeats, role = Role.Switch) { onChange(draft.copy(repeats = it)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Repeat", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (draft.repeats) "Done moves the task to its next date." else "The task is done once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = draft.repeats, onCheckedChange = null)
        }
        if (draft.repeats) RepeatDetails(draft, onChange)
    }
}

/** Every N units, and how the next date counts. */
@Composable
private fun RepeatDetails(draft: TaskDraft, onChange: (TaskDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = draft.every,
                onValueChange = { text -> onChange(draft.copy(every = text.filter { it.isDigit() }.take(4))) },
                label = { Text("Every") },
                supportingText = draft.everyError?.let { { Text(it) } },
                isError = draft.everyError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
            DropdownPicker(
                label = "Unit",
                options = RepeatUnit.entries,
                selected = draft.unit,
                text = { unit -> unit?.many?.replaceFirstChar { it.uppercase() }.orEmpty() },
                onPick = { onChange(draft.copy(unit = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Column(Modifier.selectableGroup()) {
            ModeOption(
                "After each done",
                "The next date counts from the day you mark it done. For example: water the plants.",
                selected = draft.mode == RepeatMode.FromCompletion,
            ) { onChange(draft.copy(mode = RepeatMode.FromCompletion)) }
            ModeOption(
                "On a fixed schedule",
                "The next date counts from the due date. For example: the rent on the 1st of each month.",
                selected = draft.mode == RepeatMode.FromDueDate,
            ) { onChange(draft.copy(mode = RepeatMode.FromDueDate)) }
        }
    }
}

@Composable
private fun ModeOption(title: String, explanation: String, selected: Boolean, select: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = select).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Open" with "Mark done", or "Done" with "Undo". A repeated task can always be marked done. */
@Composable
private fun DoneRow(task: TaskItem, busy: Boolean, done: () -> Unit, undo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!task.isDone) {
            Button(onClick = done, enabled = !busy) {
                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mark done")
            }
        }
        if (task.completions.isNotEmpty()) {
            OutlinedButton(onClick = undo, enabled = !busy) {
                Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (task.isDone) "Open again" else "Undo the last done")
            }
        }
    }
}

/** The details as text, for a read-only user. */
@Composable
private fun TaskFacts(task: TaskItem, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(task.title, style = MaterialTheme.typography.titleLarge)
        Fact("Status", if (task.isDone) "Done" else "Open")
        Fact("Due", task.dueOn?.let { "${formatDay(it)} (${dueText(it, today)})" } ?: "No date")
        task.repeat?.let { Fact("Repeat", repeatLongText(it)) }
        if (task.tags.isNotEmpty()) Fact("Tags", task.tags.joinToString(", "))
        task.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The links: each document or transaction, which a tap opens; and the two link buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksBlock(
    links: List<TaskLink>,
    state: TaskState,
    canWrite: Boolean,
    openDocument: (String) -> Unit,
    openTransaction: (String) -> Unit,
    unlink: (TaskLink) -> Unit,
    pick: (LinkPicker) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val known = links.filter { it.kind == TaskLinkKinds.DOCUMENT || it.kind == TaskLinkKinds.TRANSACTION }
        if (known.isEmpty()) {
            Text("Not linked to a document or a transaction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        known.forEach { link ->
            val document = link.kind == TaskLinkKinds.DOCUMENT
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable { if (document) openDocument(link.targetId) else openTransaction(link.targetId) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (document) Icons.Outlined.Description else Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (document) DocumentLine(state.documents[link.targetId]) else TransactionLine(state.transactions[link.targetId])
                }
                if (canWrite) {
                    IconButton(onClick = { unlink(link) }, enabled = !state.busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
                }
            }
        }
        if (canWrite) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick(LinkPicker.Document) }, enabled = !state.busy) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link to a document")
                }
                OutlinedButton(onClick = { pick(LinkPicker.Transaction) }, enabled = !state.busy) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link to a transaction")
                }
            }
        }
    }
}

@Composable
private fun DocumentLine(found: Linked<DocumentSummary>?) {
    when (found) {
        is Linked.Found -> {
            Text(found.value.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("Documents", found.value.kind.label, found.value.expiresOn?.let { "expires ${formatDay(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Linked.Missing -> Text("A document that Documents no longer has", style = MaterialTheme.typography.bodyMedium)
        Linked.Failed -> Text("Could not load the document", style = MaterialTheme.typography.bodyMedium)
        Linked.Loading, null -> Skeleton(lines = 2, lineHeight = 12.dp)
    }
}

@Composable
private fun TransactionLine(found: Linked<Transaction>?) {
    when (found) {
        is Linked.Found -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(found.value.description, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.format(found.value.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                listOfNotNull("Money", formatDay(found.value.date), found.value.accountName.ifBlank { null }, if (found.deleted) "Deleted" else null)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Linked.Missing -> Text("A transaction that Money no longer has", style = MaterialTheme.typography.bodyMedium)
        Linked.Failed -> Text("Could not load the transaction", style = MaterialTheme.typography.bodyMedium)
        Linked.Loading, null -> Skeleton(lines = 2, lineHeight = 12.dp)
    }
}

/** The last times the task was done, newest first. */
@Composable
private fun CompletionsBlock(task: TaskItem) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Last done", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (task.completions.isEmpty()) {
            Text("Not done yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        task.completions.forEach { completion ->
            Text(
                listOfNotNull(
                    completion.doneAt?.let { formatDay(it) },
                    completion.dueOn?.let { "due ${formatDay(it, year = false)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A picker in a sheet: a search, and the candidates; a tap on one links it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSheet(
    title: String,
    searchHint: String,
    candidates: LoadState<List<T>>,
    empty: (String) -> String,
    search: (String) -> Unit,
    onDismiss: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    search(it)
                },
                placeholder = { Placeholder(searchHint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when (candidates) {
                LoadState.Loading -> Skeleton(lines = 5, lineHeight = 28.dp, modifier = Modifier.padding(vertical = 8.dp))
                is LoadState.Failed -> ErrorState(candidates.message, onRetry = { search(text) })
                is LoadState.Loaded -> if (candidates.value.isEmpty()) {
                    Text(
                        empty(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(candidates.value) { row(it) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CandidateRow(title: String, subtitle: String, amount: String?, enabled: Boolean, pick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = pick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        amount?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
    }
}
