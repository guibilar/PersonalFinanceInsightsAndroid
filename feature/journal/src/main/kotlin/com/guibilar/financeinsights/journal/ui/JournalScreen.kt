package com.guibilar.financeinsights.journal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.journal.JournalEntrySummary
import com.guibilar.financeinsights.core.journal.JournalSummary
import com.guibilar.financeinsights.core.journal.journalDay
import com.guibilar.financeinsights.core.journal.longDayText
import com.guibilar.financeinsights.core.journal.onThisDayText
import com.guibilar.financeinsights.core.journal.shortDayText
import com.guibilar.financeinsights.core.journal.streakText
import com.guibilar.financeinsights.core.journal.weekdayLetter
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import java.time.LocalDate

/**
 * The Journal screen (design canvas, "Journal"): the streak, the week (a tap opens a day), the entry of one day in an
 * editor, "On this day" (for today), and all entries with a search. [canWrite] false (a read-only user) makes the
 * editor read only, with no Save or Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    model: JournalViewModel,
    contentPadding: PaddingValues,
    canWrite: Boolean = true,
    /** Back to where the screen was opened from; none in previews and tests. */
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Journal", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    state.summary.valueOrNull?.let { summary ->
                        streakText(summary.streak)?.let { StreakBadge(it) }
                    }
                },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (val summary = state.summary) {
                            LoadState.Loading -> item { Skeleton(lines = 5, lineHeight = 32.dp) }
                            is LoadState.Failed -> item { ErrorState(summary.message, model::refresh) }
                            is LoadState.Loaded -> {
                                item(key = "week") { WeekStrip(summary.value, state.day, model::openDay) }
                                item(key = "editor") {
                                    Editor(state, canWrite, model::edit, model::save, { confirmingDelete = true }, { model.openDay(state.today) }, model::refresh)
                                }
                                if (state.isToday && summary.value.onThisDay.isNotEmpty()) onThisDay(summary.value, model::openDay)
                            }
                        }
                        entries(state, model::search, model::openDay)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete the entry of ${longDayText(state.day, state.today)}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    model.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StreakBadge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** The last 7 days: a filled circle for a day with an entry, an outline around the open day. */
@Composable
private fun WeekStrip(summary: JournalSummary, open: LocalDate, openDay: (LocalDate) -> Unit) {
    val today = journalDay(summary.date) ?: open
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        summary.week.forEach { item ->
            val day = journalDay(item.day) ?: return@forEach
            val selected = day == open
            val label = longDayText(day, today) + if (item.written) ", written" else ", not written"
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { openDay(day) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        this.selected = selected
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    weekdayLetter(day),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val fill = if (item.written) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.written) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** The entry of the open day: the long day, the text, Save and Delete. */
@Composable
private fun Editor(
    state: JournalState,
    canWrite: Boolean,
    edit: (String) -> Unit,
    save: () -> Unit,
    delete: () -> Unit,
    backToToday: () -> Unit,
    retry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    longDayText(state.day, state.today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!state.isToday) TextButton(onClick = backToToday) { Text("Back to today") }
            }
            when (val entry = state.entry) {
                LoadState.Loading -> Skeleton(lines = 3)
                is LoadState.Failed -> ErrorState(entry.message, retry)
                is LoadState.Loaded -> {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = edit,
                        readOnly = !canWrite,
                        placeholder = { if (canWrite) Placeholder("What happened today?") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 24.sp),
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canWrite) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = save, enabled = state.changed && state.text.isNotBlank() && !state.busy) {
                                Text(if (state.busy) "Saving…" else "Save")
                            }
                            if (entry.value != null) {
                                TextButton(onClick = delete, enabled = !state.busy) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    } else {
                        Text(
                            "You can only read the journal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.onThisDay(summary: JournalSummary, openDay: (LocalDate) -> Unit) {
    item(key = "otd-title") { SectionTitle("On this day") }
    summary.onThisDay.forEach { memory ->
        item(key = "otd-${memory.entry.id}") {
            val day = journalDay(memory.entry.day) ?: return@item
            Card(
                onClick = { openDay(day) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        onThisDayText(day, memory.yearsAgo),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(memory.entry.excerpt, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif))
                }
            }
        }
    }
}

private fun LazyListScope.entries(state: JournalState, search: (String) -> Unit, openDay: (LocalDate) -> Unit) {
    item(key = "entries-title") {
        SectionTitle(if (state.totalCount > 0) "Entries · ${state.totalCount}" else "Entries")
    }
    item(key = "search") {
        OutlinedTextField(
            value = state.search,
            onValueChange = search,
            placeholder = { Placeholder("Search the journal") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (state.search.isNotEmpty()) IconButton(onClick = { search("") }) { Icon(Icons.Outlined.Close, "Clear the search") } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when (val entries = state.entries) {
        LoadState.Loading -> item { Skeleton(lines = 4, lineHeight = 32.dp) }
        is LoadState.Failed -> item { ErrorState(entries.message, { search(state.search) }) }
        is LoadState.Loaded -> if (entries.value.isEmpty()) {
            item(key = "no-entries") {
                Text(
                    if (state.search.isBlank()) "No entries yet. Write the first one for today." else "No entry matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "entries") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        entries.value.forEachIndexed { index, entry ->
                            EntryRow(entry, entry.day == state.day.toString(), openDay)
                            if (index < entries.value.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            if (state.totalCount > entries.value.size) {
                item(key = "more") {
                    Text(
                        "The newest ${entries.value.size} of ${state.totalCount}. Search to find older entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: JournalEntrySummary, open: Boolean, openDay: (LocalDate) -> Unit) {
    val day = journalDay(entry.day) ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button) { openDay(day) }
            .then(if (open) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(shortDayText(day), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            entry.excerpt,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}
