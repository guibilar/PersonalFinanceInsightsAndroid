package com.guibilar.financeinsights.home.ui

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.home.HomeSummary
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.Maintenance
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.RoomCount
import com.guibilar.financeinsights.core.home.daysLeftText
import com.guibilar.financeinsights.core.home.exportFileName
import com.guibilar.financeinsights.core.home.homeDay
import com.guibilar.financeinsights.core.home.itemsText
import com.guibilar.financeinsights.core.home.repeatText
import com.guibilar.financeinsights.core.home.roomLabel
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A maintenance job is "soon" this many days before its due date: its badge then uses the warning colour. */
private const val SOON_DAYS = 7L

/**
 * The Home screen (design canvas, "Home"): a search over all items, "Maintenance" (the repeated tasks of the items,
 * with the days left), "Inventory" with the item count and the total value, the rooms, and "Share list for
 * insurance". [canWrite] false (a read-only user) hides "Add item" and "Add room"; the export still works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    model: HomeViewModel,
    contentPadding: PaddingValues,
    openRoom: (String) -> Unit,
    openItem: (String) -> Unit,
    openTask: (String) -> Unit,
    addItem: () -> Unit,
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
    var addingRoom by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun export() {
        scope.launch {
            val bytes = model.export() ?: return@launch
            if (!shareCsv(context, exportFileName(state.today), bytes)) model.say("No app on this phone can take the list.")
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Home", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (canWrite) IconButton(onClick = addItem) { Icon(Icons.Outlined.Add, "Add item") } },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "search") { SearchField(state.query.text, model::search, model::clearSearch) }
                        if (state.query.isActive) {
                            results(state, openItem, model::clearSearch)
                        } else {
                            when (val summary = state.summary) {
                                LoadState.Loading -> item { Skeleton(lines = 6, lineHeight = 32.dp) }
                                is LoadState.Failed -> item { ErrorState(summary.message, model::refresh) }
                                is LoadState.Loaded -> home(
                                    summary.value, state, canWrite, openRoom, openTask,
                                    addRoom = { addingRoom = true },
                                    showEnding = { model.showEnding(true) },
                                    export = ::export,
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (addingRoom) {
        NameSheet("Add a room", "", "Add", save = model::addRoom, onDismiss = { addingRoom = false })
    }
}

/** The Home screen without a search: maintenance, inventory, rooms, and the export. */
private fun LazyListScope.home(
    summary: HomeSummary,
    state: HomeState,
    canWrite: Boolean,
    openRoom: (String) -> Unit,
    openTask: (String) -> Unit,
    addRoom: () -> Unit,
    showEnding: () -> Unit,
    export: () -> Unit,
) {
    val empty = summary.itemCount == 0 && summary.rooms.isEmpty()
    if (empty) {
        item(key = "empty") {
            EmptyState(
                if (canWrite) "Nothing here yet. Add a room, then the things in it: for example, the dishwasher in the kitchen."
                else "Nothing here yet.",
            )
        }
    } else {
        item(key = "maintenance-title") { SectionTitle("Maintenance") }
        item(key = "maintenance") { MaintenanceCard(state.maintenance, state.today, openTask) }
        item(key = "inventory-title") {
            SectionTitle("Inventory · ${itemsText(summary.itemCount)}", trailing = "${Money.format(summary.totalValue)} value")
        }
        if (summary.warrantiesEnding > 0) item(key = "ending") { EndingLine(summary.warrantiesEnding, showEnding) }
        summary.rooms.chunked(2).forEachIndexed { index, pair ->
            item(key = "rooms-$index") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { room -> RoomTile(room, Modifier.weight(1f)) { openRoom(room.roomId ?: NO_ROOM) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (canWrite) {
        item(key = "add-room") {
            OutlinedButton(onClick = addRoom, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add room")
            }
        }
    }
    if (summary.itemCount > 0) {
        item(key = "export") {
            OutlinedButton(onClick = export, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.exporting) "Preparing the list…" else "Share list for insurance")
            }
        }
    }
}

/** The search results, or the warranties that end soon. */
private fun LazyListScope.results(state: HomeState, openItem: (String) -> Unit, clear: () -> Unit) {
    if (state.query.endingOnly) {
        item(key = "ending-only") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Warranties that end in the next 30 days",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = clear) { Text("Show all") }
            }
        }
    }
    when (val results = state.results) {
        LoadState.Loading -> item { Skeleton(lines = 4, lineHeight = 32.dp) }
        is LoadState.Failed -> item { ErrorState(results.message, clear) }
        is LoadState.Loaded -> if (results.value.isEmpty()) {
            item {
                EmptyState(if (state.query.text.isBlank()) "No warranty ends soon." else "No item matches “${state.query.text.trim()}”.")
            }
        } else {
            item(key = "results") { ItemsCard(results.value, state.today, { item: ItemSummary -> openItem(item.id) }, withRoom = true) }
        }
    }
}

@Composable
private fun SearchField(text: String, search: (String) -> Unit, clear: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = search,
        placeholder = { Placeholder("Search all items") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = clear) { Icon(Icons.Outlined.Close, "Clear the search") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The repeated tasks of the items, each with its rule and the days left. */
@Composable
private fun MaintenanceCard(jobs: LoadState<List<Maintenance>>, today: LocalDate, open: (String) -> Unit) {
    when (jobs) {
        LoadState.Loading -> Skeleton(lines = 2, lineHeight = 28.dp)
        is LoadState.Failed -> Text(jobs.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        is LoadState.Loaded -> if (jobs.value.isEmpty()) {
            Text(
                "No maintenance jobs yet. Add one on an item: for example, clean the filter every month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RowsCard {
                jobs.value.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                    MaintenanceRow(task, today) { open(task.taskId) }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceRow(task: Maintenance, today: LocalDate, open: () -> Unit) {
    val due = homeDay(task.dueOn)
    val soon = due != null && ChronoUnit.DAYS.between(today, due) <= SOON_DAYS
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = open).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // The item (T89), and the repeat of a repeated task.
            Text(
                listOfNotNull(task.itemName, repeatText(task.repeatEvery, task.repeatUnit)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        daysLeftText(task.dueOn, today)?.let {
            Spacer(Modifier.width(8.dp))
            Badge(it, strong = soon)
        }
    }
}

/** "1 warranty ends in the next 30 days", which lists them. */
@Composable
private fun EndingLine(count: Int, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button, onClick = open),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            if (count == 1) "1 warranty ends in the next 30 days" else "$count warranties end in the next 30 days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A room in the grid: its name, its item count and its value. */
@Composable
private fun RoomTile(room: RoomCount, modifier: Modifier, open: () -> Unit) {
    Card(
        onClick = open,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(roomLabel(room.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (room.itemCount == 0) "No items" else "${itemsText(room.itemCount)} · ${Money.format(room.totalValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
