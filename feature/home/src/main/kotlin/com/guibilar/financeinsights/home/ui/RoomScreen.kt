package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.NO_ROOM_NAME
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.core.home.itemsText
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import java.time.LocalDate

/**
 * One room: its items by name, with the value and the warranty state. "Add item" adds one in this room. A real room
 * can be renamed, and deleted when it has no items. [canWrite] false (a read-only user) hides all three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    model: RoomViewModel,
    contentPadding: PaddingValues,
    today: LocalDate,
    openItem: (String) -> Unit,
    addItem: () -> Unit,
    onBack: () -> Unit,
    canWrite: Boolean = true,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var movingItems by rememberSaveable { mutableStateOf(false) }
    val items = state.items.valueOrNull

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText(state.name, fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite) {
                        IconButton(onClick = addItem) { Icon(Icons.Outlined.Add, "Add item") }
                        if (model.isRoom) {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                        onClick = {
                                            menu = false
                                            renaming = true
                                        },
                                    )
                                    // T89: the place of the room in the list.
                                    DropdownMenuItem(
                                        text = { Text("Move up") },
                                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                                        enabled = state.index > 0 && !state.busy,
                                        onClick = {
                                            menu = false
                                            model.move(-1)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move down") },
                                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                                        enabled = state.index in 0 until state.rooms.lastIndex && !state.busy,
                                        onClick = {
                                            menu = false
                                            model.move(1)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete room") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            menu = false
                                            deleting = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = false, onRefresh = { model.reload() }, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (val loaded = state.items) {
                            LoadState.Loading -> item { Skeleton(lines = 6, lineHeight = 32.dp) }
                            is LoadState.Failed -> item { ErrorState(loaded.message, { model.reload() }) }
                            is LoadState.Loaded -> if (loaded.value.isEmpty()) {
                                item {
                                    EmptyState(
                                        if (canWrite) "No items in ${state.name} yet. Add one with the + button." else "No items in ${state.name} yet.",
                                    )
                                }
                            } else {
                                item(key = "count") {
                                    Text(
                                        "${itemsText(loaded.value.size)} · ${Money.format(loaded.value.sumOf { it.value })} value",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                item(key = "items") { ItemsCard(loaded.value, today, { openItem(it.id) }) }
                                if (canWrite) {
                                    // T89: move items to another room (for example before a room delete).
                                    item(key = "move-items") {
                                        OutlinedButton(onClick = { movingItems = true }, enabled = !state.busy) {
                                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Move items")
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

    if (movingItems && items != null) {
        MoveItemsSheet(
            items = items,
            rooms = state.rooms.filter { it.id != state.room?.id },
            canLeaveRoom = model.isRoom,
            move = { ids, roomId ->
                movingItems = false
                model.moveItems(ids, roomId)
            },
            onDismiss = { movingItems = false },
        )
    }
    if (renaming) {
        NameSheet("Rename the room", state.name, "Save", save = model::rename, onDismiss = { renaming = false })
    }
    if (deleting) {
        val hasItems = !items.isNullOrEmpty()
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(if (hasItems) "The room has items" else "Delete the room?") },
            text = {
                Text(
                    if (hasItems) "Move its items to another room, or delete them, first. Then you can delete ${state.name}."
                    else "“${state.name}” will be deleted.",
                )
            },
            confirmButton = {
                if (hasItems) {
                    TextButton(onClick = { deleting = false }) { Text("OK") }
                } else {
                    TextButton(onClick = {
                        deleting = false
                        model.delete()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = if (hasItems) null else ({ TextButton(onClick = { deleting = false }) { Text("Cancel") } }),
        )
    }
}

/** "Move items" (T89): the items to move (all checked at first), and the room to move them to. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoveItemsSheet(
    items: List<ItemSummary>,
    rooms: List<Room>,
    canLeaveRoom: Boolean,
    move: (itemIds: List<String>, roomId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(items.map { it.id }) }
    // The target: a room id, NO_ROOM for no room, or null before a choice.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Move items", style = MaterialTheme.typography.titleLarge)
            Text("To", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    FilterChip(selected = target == room.id, onClick = { target = room.id }, label = { Text(room.name) })
                }
                if (canLeaveRoom) {
                    FilterChip(selected = target == NO_ROOM, onClick = { target = NO_ROOM }, label = { Text(NO_ROOM_NAME) })
                }
            }
            Text("Items", style = MaterialTheme.typography.titleSmall)
            items.forEach { item ->
                val checked = item.id in chosen
                Row(
                    Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox) {
                        chosen = if (checked) chosen - item.id else chosen + item.id
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { target?.let { move(chosen, if (it == NO_ROOM) null else it) } },
                    enabled = target != null && chosen.isNotEmpty(),
                ) { Text("Move ${itemsText(chosen.size)}") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
