package com.guibilar.financeinsights.tasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.tasks.TaskSummary
import com.guibilar.financeinsights.core.tasks.UPCOMING_DAYS
import com.guibilar.financeinsights.core.tasks.dueText
import com.guibilar.financeinsights.core.tasks.parseDay
import com.guibilar.financeinsights.core.tasks.recurringText
import com.guibilar.financeinsights.core.tasks.repeatText
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Tasks screen (design canvas, "Tasks"): the quick add (a title, Enter adds it, due today unless the chip picks
 * another day), then "Overdue", "Today", "Upcoming", "Later and no date", "Recurring", and a collapsed "Done". A
 * checkbox marks a task done, with "Undo" in the message. A tap on a task opens [openTask] (the task sheet).
 * [canWrite] false (a read-only user) hides the quick add and disables the checkboxes. [focusQuickAdd] puts the
 * cursor in the quick add at once (Capture).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    model: TasksViewModel,
    contentPadding: PaddingValues,
    openTask: (TaskSummary) -> Unit,
    canWrite: Boolean = true,
    focusQuickAdd: Boolean = false,
    /** Back to where the screen was opened from; none in previews and tests. */
    onBack: (() -> Unit)? = null,
    /** The task sheet, over the screen; it is part of the screen, so the snackbar shows above the bar. */
    sheet: @Composable () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val sections = state.sections
    val today = state.today

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Tasks", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        if (searching) model.search("")
                        searching = !searching
                    }) {
                        Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, if (searching) "Close the search" else "Search tasks")
                    }
                },
            )
            ReadableColumn {
                Column {
                    if (searching) SearchField(state.query, model::search)
                    if (canWrite && !searching) QuickAdd(model, today, focusQuickAdd)
                    if (state.tags.isNotEmpty() && !state.overdueOnly) TagChips(state.tags, state.tag, model::filterTag)
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when {
                                state.loading && state.isEmpty -> item { Skeleton(lines = 6, lineHeight = 32.dp) }
                                state.error != null -> item { ErrorState(state.error!!, model::refresh) }
                                state.overdueOnly -> {
                                    item(key = "overdue-only") { OverdueOnlyNote(model::showAll) }
                                    if (sections.overdue.isEmpty()) {
                                        item { EmptyState("No task is overdue.") }
                                    } else {
                                        section("Overdue", sections.overdue, today, state.busy, canWrite, model::done, openTask, showDue = true)
                                    }
                                }
                                state.isEmpty -> item {
                                    EmptyState(
                                        if (canWrite) "No tasks yet. Add one above: for example, water the plants every 4 days."
                                        else "No tasks yet.",
                                    )
                                }
                                state.isFiltered && state.shownOpen.isEmpty() && state.shownDone.isEmpty() -> item {
                                    EmptyState("No task matches the search or the tag.")
                                }
                                else -> {
                                    if (sections.isEmpty) item { EmptyState("All done. Nothing is open.") }
                                    section("Overdue", sections.overdue, today, state.busy, canWrite, model::done, openTask, showDue = true)
                                    section("Today", sections.today, today, state.busy, canWrite, model::done, openTask, showDue = false)
                                    section("Upcoming", sections.upcoming, today, state.busy, canWrite, model::done, openTask, showDue = true)
                                    section("Later and no date", sections.later, today, state.busy, canWrite, model::done, openTask, showDue = true)
                                    if (sections.recurring.isNotEmpty()) {
                                        item(key = "recurring-title") { SectionTitle("Recurring") }
                                        item(key = "recurring") { RecurringCard(sections.recurring, today, openTask) }
                                    }
                                    if (state.shownDone.isNotEmpty()) {
                                        item(key = "done-title") { DoneTitle(state.shownDone.size, showDone) { showDone = !showDone } }
                                        if (showDone) {
                                            item(key = "done") {
                                                TaskCard(state.shownDone, today, state.busy, canWrite, onCheck = { model.undo(it.id) }, open = openTask, showDue = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    sheet()
}

/** The search field (T86): words in the title, the notes, or the tags. */
@Composable
private fun SearchField(text: String, search: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = search,
        placeholder = { Text("Search titles, notes, tags") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus),
    )
}

/** The tag filter (T86): one chip per tag; a tap selects the tag, a second tap clears it. */
@Composable
private fun TagChips(tags: List<String>, selected: String?, select: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        items(tags, key = { it }) { tag ->
            FilterChip(selected = tag == selected, onClick = { select(tag) }, label = { Text("#$tag") })
        }
    }
}

/** A section: its title with the count, and its tasks in one card. Nothing when it has no tasks. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    tasks: List<TaskSummary>,
    today: LocalDate,
    busy: Set<String>,
    canWrite: Boolean,
    done: (TaskSummary) -> Unit,
    open: (TaskSummary) -> Unit,
    showDue: Boolean,
) {
    if (tasks.isEmpty()) return
    item(key = "$title-title") { SectionTitle("$title · ${tasks.size}") }
    item(key = title) { TaskCard(tasks, today, busy, canWrite, onCheck = done, open = open, showDue = showDue) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
}

/** "Done · 20", which opens and closes the done tasks. */
@Composable
private fun DoneTitle(count: Int, open: Boolean, toggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button, onClick = toggle).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Done · $count",
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (open) "Hide the done tasks" else "Show the done tasks",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Only the overdue tasks", with "Show all". */
@Composable
private fun OverdueOnlyNote(showAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Only the overdue tasks",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = showAll) { Text("Show all") }
    }
}

/** Tasks in one card, each with its checkbox. A done task is crossed out, and unchecking it undoes the done. */
@Composable
private fun TaskCard(
    tasks: List<TaskSummary>,
    today: LocalDate,
    busy: Set<String>,
    canWrite: Boolean,
    onCheck: (TaskSummary) -> Unit,
    open: (TaskSummary) -> Unit,
    showDue: Boolean,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        tasks.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
            TaskRow(task, today, busy = task.id in busy, canWrite = canWrite, onCheck = { onCheck(task) }, open = { open(task) }, showDue = showDue)
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskSummary,
    today: LocalDate,
    busy: Boolean,
    canWrite: Boolean,
    onCheck: () -> Unit,
    open: () -> Unit,
    showDue: Boolean,
) {
    val late = !task.isDone && parseDay(task.dueOn)?.let { it < today } == true
    val details = listOfNotNull(
        if (showDue || late) dueText(task.dueOn, today) else null,
        task.repeat?.let(::repeatText),
        task.doneAt?.takeIf { task.isDone }?.let { "Done ${formatDay(it, year = false)}" },
        task.tags.takeIf { it.isNotEmpty() }?.joinToString(", "),
    )
    val links = linkLabels(task)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = open).padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = 4.dp, end = 4.dp)) {
            Checkbox(
                checked = task.isDone,
                onCheckedChange = { onCheck() },
                enabled = canWrite && !busy,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty() || links.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) LocalChartColors.current.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (links.isNotEmpty()) {
                        Text(
                            links.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The repeated tasks: each with its rule and next date. A tap opens the task. */
@Composable
private fun RecurringCard(tasks: List<TaskSummary>, today: LocalDate, open: (TaskSummary) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        tasks.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open(task) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        recurringText(task, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The quick add: a title (Enter adds it) and a chip for the due date (today by default). */
@Composable
private fun QuickAdd(model: TasksViewModel, today: LocalDate, focus: Boolean) {
    var title by rememberSaveable { mutableStateOf("") }
    // "yyyy-MM-dd", or null for no date. Today by default.
    var due by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    var menu by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state by model.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }

    fun add() {
        if (title.isBlank() || state.adding) return
        scope.launch {
            if (model.add(title, parseDay(due))) {
                title = ""
                due = today.toString()
            }
        }
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Placeholder("Add a task") },
            leadingIcon = { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (title.isNotBlank()) IconButton(onClick = ::add, enabled = !state.adding) { Icon(Icons.AutoMirrored.Outlined.Send, "Add the task") }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Box {
            AssistChip(
                onClick = { menu = true },
                label = { Text(quickDueLabel(parseDay(due), today)) },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    "Today" to today,
                    "Tomorrow" to today.plusDays(1),
                    "Next week" to today.plusDays(UPCOMING_DAYS),
                    "No date" to null,
                ).forEach { (label, day) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        due = day?.toString()
                        menu = false
                    })
                }
                DropdownMenuItem(text = { Text("Pick a day…") }, onClick = {
                    menu = false
                    picking = true
                })
            }
        }
    }
    if (picking) {
        PickDayDialog(parseDay(due) ?: today, onPick = { due = it.toString() }, onDismiss = { picking = false })
    }
}

/** The label of the quick add's date chip: "Due today", "Due tomorrow", "Due Oct 20", "No date". */
internal fun quickDueLabel(due: LocalDate?, today: LocalDate): String = when (due) {
    null -> "No date"
    today -> "Due today"
    today.plusDays(1) -> "Due tomorrow"
    else -> "Due ${formatDay(due, year = due.year != today.year)}"
}
