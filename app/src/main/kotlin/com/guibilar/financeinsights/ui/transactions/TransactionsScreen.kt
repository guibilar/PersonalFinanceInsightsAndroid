package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.DateChoice
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.logic.dateRange
import com.guibilar.financeinsights.core.logic.describeRange
import com.guibilar.financeinsights.core.logic.matchDatePreset
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton

private val SORT_LABELS = mapOf(
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
    sharedFile: PickedFile? = null,
    onSharedFileTaken: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Transaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<PickedFile?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var confirmRules by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // A statement shared to the app opens the import, ready to go.
    LaunchedEffect(sharedFile) {
        if (sharedFile != null) {
            importing = sharedFile
            importOpen = true
            onSharedFileTaken()
        }
    }
    ShowMessages(model.messages, snackbar)
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

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (state.showDeleted) "Deleted transactions" else "Transactions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (state.showDeleted) IconButton(onClick = model::toggleDeleted) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to transactions") }
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
                            onImport = {
                                importing = null
                                importOpen = true
                            },
                            onApplyRules = { confirmRules = true },
                            onShowDeleted = model::toggleDeleted,
                        )
                    }
                    AccountMenuButton(account, onSignOut)
                },
            )
            OutlinedTextField(
                value = state.search,
                onValueChange = model::search,
                placeholder = { Text("Search descriptions") },
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
                    items(chips, key = { it.label }) { chip ->
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
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (!state.showDeleted) item { Box(Modifier.padding(16.dp)) { BreakdownPanel(state, model) } }
                    else item {
                        Text(
                            "Deleted transactions are left out of lists, reports and budgets. Tap one to restore it.",
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
                                    OutlinedButton(onClick = { importOpen = true }) { Text("Import a statement") }
                                } else {
                                    EmptyState("No transactions match these filters.")
                                    OutlinedButton(onClick = model::clearAll) { Text("Clear filters") }
                                }
                            }
                        }
                        else -> {
                            items(list.items, key = { it.id }) { transaction ->
                                TransactionRow(transaction, onClick = { opened = transaction })
                                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
        }

        if (!state.showDeleted) {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.showDeleted) 0.dp else FAB_CLEARANCE))
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
            )
        }
    }
    if (adding) AddTransactionSheet(state, model, onDismiss = { adding = false })
    if (importOpen) ImportSheet(state.accounts, model, onDismiss = { importOpen = false }, initialFile = importing)
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

@Composable
private fun MoreMenu(onImport: () -> Unit, onApplyRules: () -> Unit, onShowDeleted: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Import a statement") },
            leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
            onClick = { open = false; onImport() },
        )
        DropdownMenuItem(
            text = { Text("Apply the rules") },
            leadingIcon = { Icon(Icons.Outlined.Rule, null) },
            onClick = { open = false; onApplyRules() },
        )
        DropdownMenuItem(
            text = { Text("Deleted transactions") },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
            onClick = { open = false; onShowDeleted() },
        )
    }
}

/** A criterion that is set, as a removable chip. */
private data class FilterChipModel(val label: String, val remove: () -> Unit)

private fun filterChips(state: TransactionsState, model: TransactionsViewModel): List<FilterChipModel> {
    val criteria = state.criteria
    return buildList {
        criteria.description?.let { add(FilterChipModel("“$it”", model::clearSearch)) }
        criteria.categoryId?.let { id ->
            add(FilterChipModel(state.categories.firstOrNull { it.id == id }?.name ?: "Category") { model.setCategory(null) })
        }
        criteria.accountId?.let { id ->
            add(FilterChipModel(state.accounts.firstOrNull { it.id == id }?.name ?: "Account") { model.setAccount(null) })
        }
        criteria.flow?.let { add(FilterChipModel(it.label) { model.setFlow(null) }) }
        when (val choice = matchDatePreset(criteria, state.today)) {
            DateChoice.All -> {}
            is DateChoice.Preset -> add(FilterChipModel("${choice.preset.label} · ${describeRange(criteria.dateRange())}") { model.setDates(DateRange()) })
            DateChoice.Custom -> add(FilterChipModel(describeRange(criteria.dateRange())) { model.setDates(DateRange()) })
        }
    }
}

@Composable
private fun SortMenu(field: TransactionSortField, direction: SortDirection, onSort: (TransactionSortField) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        SORT_LABELS.forEach { (option, label) ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontWeight = if (option == field) FontWeight.SemiBold else null)
                    }
                },
                trailingIcon = {
                    if (option == field) {
                        Icon(
                            if (direction == SortDirection.Ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            if (direction == SortDirection.Ascending) "Ascending" else "Descending",
                        )
                    }
                },
                onClick = {
                    open = false
                    onSort(option)
                },
            )
        }
    }
}
