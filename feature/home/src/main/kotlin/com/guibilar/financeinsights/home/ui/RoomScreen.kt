package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (renaming) {
        NameDialog("Rename the room", state.name, "Save", save = model::rename, onDismiss = { renaming = false })
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
