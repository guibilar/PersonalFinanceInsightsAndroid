package com.guibilar.financeinsights.ui.transactions

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberFabExpansion
import com.guibilar.financeinsights.ui.common.rememberOpenItem

internal val SORT_LABELS = mapOf(
    TransactionSortField.Date to "Date",
    TransactionSortField.Amount to "Amount",
    TransactionSortField.Description to "Description",
    TransactionSortField.Category to "Category",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    model: TransactionsViewModel,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    /** Back to the Money home; none in previews and tests. */
    onBack: (() -> Unit)? = null,
    sharedFiles: List<PickedFile>? = null,
    onSharedFileTaken: () -> Unit = {},
    addRequested: Boolean = false,
    onAddTaken: () -> Unit = {},
    importRequested: Boolean = false,
    onImportTaken: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    // Back from the background on a new day: today moves on.
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    // Saved, so a sheet stays open (with what was typed) when the phone rotates.
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var opened by rememberOpenItem(state.list.items) { it.id }
    var adding by rememberSaveable { mutableStateOf(false) }
    val importState by model.importState.collectAsState()
    var confirmRules by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Statements shared to the app open the import, ready to go.
    LaunchedEffect(sharedFiles) {
        if (sharedFiles != null) {
            model.openImport(sharedFiles)
            onSharedFileTaken()
        }
    }
    ShowMessages(model.messages, snackbar)
    // "Add transaction" from the launcher shortcut or the widget.
    LaunchedEffect(addRequested) {
        if (addRequested) {
            if (state.showDeleted) model.toggleDeleted()
            adding = true
            onAddTaken()
        }
    }
    // "Import statements" from the dashboard.
    LaunchedEffect(importRequested) {
        if (importRequested) {
            model.openImport()
            onImportTaken()
        }
    }
    val listState = rememberLazyListState()
    val chips = filterChips(state, model)

    // The next page loads as the list nears its end.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd, state.list.items.size) { if (nearEnd) model.loadMore() }

    // New filters, order or view start the list from its top (not part-way down the old one); not on first show,
    // so coming back to the tab keeps the place.
    var listedFor by rememberSaveable { mutableStateOf<String?>(null) }
    val listKey = "${state.criteria}|${state.sortBy}|${state.sortDirection}|${state.showDeleted}"
    LaunchedEffect(listKey) {
        if (listedFor != null && listedFor != listKey) listState.scrollToItem(0)
        listedFor = listKey
    }
    // A linked transaction: its row comes into view and is highlighted for a short time. After the effect above, so
    // this scroll wins when new filters and the row arrive together.
    val reducedMotion = rememberReducedMotion()
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(state.highlighted) {
        val id = state.highlighted ?: return@LaunchedEffect
        val index = state.list.items.indexOfFirst { it.id == id }
        // After the next frame, so the list has laid out its new rows. A row that is fully in view stays where it is.
        withFrameNanos {}
        if (index >= 0) {
            val target = LIST_HEADER_ITEMS + index
            val layout = listState.layoutInfo
            val inView = layout.visibleItemsInfo.any {
                it.index == target && it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset
            }
            if (!inView) listState.scrollToItem(target)
        }
        highlight.snapTo(1f)
        if (reducedMotion) {
            delay(LINK_HIGHLIGHT_MS.toLong())
        } else {
            // Full strength for half the time, then a fade.
            highlight.animateTo(0f, tween(durationMillis = LINK_HIGHLIGHT_MS / 2, delayMillis = LINK_HIGHLIGHT_MS / 2))
        }
        highlight.snapTo(0f)
        model.highlightShown(id)
    }
    BackHandler(enabled = state.showDeleted, onBack = model::toggleDeleted)
    // Selecting: Back clears the ticks first.
    val selecting = state.selected.isNotEmpty()
    BackHandler(enabled = selecting, onBack = model::clearSelection)

    // The bar leaves as the list scrolls down and comes back on any scroll up.
    val barBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        val fab = rememberFabExpansion()
        Box(Modifier.fillMaxSize().nestedScroll(fab.connection)) {
            Column(Modifier.fillMaxSize().nestedScroll(barBehavior.nestedScrollConnection)) {
                if (selecting) SelectionBar(state, model) else TopAppBar(
                    scrollBehavior = barBehavior,
                    title = { FitText(if (state.showDeleted) "Deleted transactions" else "Transactions", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                    navigationIcon = {
                        if (state.showDeleted) {
                            IconButton(onClick = model::toggleDeleted) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to transactions") }
                        } else if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                        }
                    },
                    actions = {
                        SortMenu(state.sortBy, state.sortDirection, model::sort)
                        IconButton(onClick = { filtersOpen = true }) {
                            BadgedBox(badge = { if (chips.isNotEmpty()) Badge { Text(chips.size.toString()) } }) {
                                Icon(Icons.Outlined.FilterList, "Filters")
                            }
                        }
                        if (!state.showDeleted) {
                            MoreMenu(
                                onImport = { model.openImport() },
                                onApplyRules = { confirmRules = true },
                                onShowDeleted = model::toggleDeleted,
                            )
                        }
                        AccountMenuButton(account, onSignOut)
                    },
                )
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    // On a tablet, a centred column rather than rows stretched across the screen.
                    ReadableColumn {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                            // The search and the filters set scroll away with the list; the bar above comes back on scrolling up.
                            item(key = "search") {
                                Column {
                                    OutlinedTextField(
                                        value = state.search,
                                        onValueChange = model::search,
                                        placeholder = { Placeholder("Search descriptions and notes") },
                                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                        trailingIcon = {
                                            if (state.search.isNotEmpty()) IconButton(onClick = model::clearSearch) { Icon(Icons.Outlined.Close, "Clear search") }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(28.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    )
                                    if (chips.isNotEmpty()) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            items(chips, key = { it.key }) { chip ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = chip.remove,
                                                    label = { Text(chip.label) },
                                                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(16.dp)) },
                                                )
                                            }
                                            if (chips.size > 1) item { TextButton(onClick = model::clearAll) { Text("Clear all") } }
                                        }
                                    }
                                    state.linkNotice?.let { notice ->
                                        LinkNoticeCard(
                                            notice,
                                            onClose = model::dismissLinkNotice,
                                            onShowDeleted = model::toggleDeleted,
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                                        )
                                    }
                                }
                            }
                            if (!state.showDeleted) item { Box(Modifier.padding(16.dp)) { BreakdownPanel(state, model) } }
                            else item {
                                Text(
                                    "Deleted transactions are left out of lists, reports and budgets. Tap one, or swipe it right, to restore it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            val list = state.list
                            when {
                                list.loading && list.items.isEmpty() -> item { Box(Modifier.padding(16.dp)) { Skeleton(lines = 8, lineHeight = 28.dp) } }
                                list.error != null -> item { ErrorState(list.error, model::refresh) }
                                list.items.isEmpty() -> item {
                                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (state.showDeleted) {
                                            EmptyState(if (state.criteria.isEmpty) "No deleted transactions." else "No deleted transactions match these filters.")
                                            OutlinedButton(onClick = model::toggleDeleted) { Text("Back to transactions") }
                                        } else if (state.criteria.isEmpty) {
                                            EmptyState("No transactions yet.")
                                            OutlinedButton(onClick = { model.openImport() }) { Text("Import a statement") }
                                        } else {
                                            EmptyState("No transactions match these filters.")
                                            OutlinedButton(onClick = model::clearAll) { Text("Clear filters") }
                                        }
                                    }
                                }
                                else -> {
                                    // The old rows while the new ones load: dimmed, and not opened (they may not match any more).
                                    val stale = list.loading && !state.refreshing
                                    items(list.items, key = { it.id }) { transaction ->
                                        Column(Modifier.alpha(if (stale) 0.5f else 1f).animateItem()) {
                                            SwipeRow(
                                                transaction = transaction,
                                                deletedList = state.showDeleted,
                                                enabled = !stale && !selecting,
                                                onDelete = { model.delete(transaction) },
                                                onRestore = { model.restore(transaction) },
                                                onCategorise = { opened = transaction },
                                            ) { actions ->
                                                // Opaque, so the swipe's colour shows only beside it. A linked row is
                                                // highlighted for a short time.
                                                val surface = MaterialTheme.colorScheme.surface
                                                val color = if (transaction.id == state.highlighted) {
                                                    lerp(surface, MaterialTheme.colorScheme.primaryContainer, highlight.value)
                                                } else {
                                                    surface
                                                }
                                                Surface(color = color) {
                                                    TransactionRow(
                                                        transaction,
                                                        onClick = {
                                                            when {
                                                                selecting -> model.toggleSelected(transaction.id)
                                                                !stale -> opened = transaction
                                                            }
                                                        },
                                                        actions = actions,
                                                        // Long-pressed, it starts a change to several at once (not in the deleted list).
                                                        onLongClick = if (state.showDeleted || stale) null else ({ model.toggleSelected(transaction.id) }),
                                                        selected = if (selecting) transaction.id in state.selected else null,
                                                    )
                                                }
                                            }
                                            HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            when {
                                                list.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                                list.moreFailed -> TextButton(onClick = model::loadMore) { Text("Could not load more. Try again") }
                                                list.endReached -> Text(
                                                    "${list.totalCount} ${if (list.totalCount == 1) "transaction" else "transactions"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.list.loading && state.list.items.isNotEmpty() && !state.refreshing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }

            if (!state.showDeleted && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Add") },
                    expanded = fab.expanded,
                    // Collapsed (after a scroll) it shows only the icon: TalkBack still says what it adds.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { contentDescription = "Add transaction" },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.showDeleted) 0.dp else FAB_CLEARANCE))
        }
    }

    if (filtersOpen) FiltersSheet(state, onDismiss = { filtersOpen = false }, model = model)
    opened?.let { transaction ->
        if (transaction.hidden || state.showDeleted) {
            DeletedTransactionSheet(transaction, model, onDismiss = { opened = null })
        } else {
            EditTransactionSheet(
                transaction = transaction,
                categories = state.categories.map { it.reference },
                budgets = state.budgets,
                model = model,
                onDismiss = { opened = null },
                tags = state.tags,
            )
        }
    }
    if (adding) AddTransactionSheet(state, model, onDismiss = { adding = false })
    if (importState.open) ImportSheet(state.accounts, model, onDismiss = model::closeImport, accountsLoaded = state.accountsLoaded)
    if (confirmRules) {
        AlertDialog(
            onDismissRequest = { confirmRules = false },
            title = { Text("Apply the rules?") },
            text = {
                Text(
                    "Every transaction gets its category and flow from the current rules again. Categories and flows you " +
                        "picked by hand, and the categories of savings transactions, are left as they are.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRules = false
                    model.applyRules()
                }) { Text("Apply rules") }
            },
            dismissButton = { TextButton(onClick = { confirmRules = false }) { Text("Cancel") } },
        )
    }
}

/** Keeps a message clear of the Add button. */
private val FAB_CLEARANCE: Dp = 72.dp


