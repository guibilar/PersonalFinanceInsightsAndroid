package com.guibilar.financeinsights.ui.budgets

import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.logic.BudgetHealth
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.budgetHealth
import com.guibilar.financeinsights.core.logic.periodName
import com.guibilar.financeinsights.core.logic.periodSuffix
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlin.math.abs

/** Warning amber: nearly at the limit. */
private val WARNING = androidx.compose.ui.graphics.Color(0xFFE0A100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(model: BudgetsViewModel, account: AccountInfo, onSignOut: suspend () -> Unit, contentPadding: PaddingValues) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var editing by remember { mutableStateOf<Budget?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Budget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    ShowMessages(model.messages, snackbar)

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) model.loadMore() }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Budgets", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                actions = { AccountMenuButton(account, onSignOut) },
            )
            OutlinedTextField(
                value = state.search,
                onValueChange = model::search,
                placeholder = { Placeholder("Search budgets") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (state.search.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "All", Status.Active to "Active", Status.Inactive to "Inactive").forEach { (status, label) ->
                    FilterChip(state.status == status, onClick = { model.setStatus(status) }, label = { Text(label) })
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().alpha(if (state.loading && state.items.isNotEmpty()) 0.5f else 1f),
                ) {
                    when {
                        state.loading && state.items.isEmpty() -> item { Skeleton(lines = 6, lineHeight = 32.dp) }
                        state.error != null -> item { ErrorState(state.error!!, model::refresh) }
                        state.items.isEmpty() -> item {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (state.hasCriteria) {
                                    EmptyState("No budgets match these filters.")
                                    OutlinedButton(onClick = model::clearAll) { Text("Clear filters") }
                                } else {
                                    EmptyState("No budgets yet. A budget sets a limit, for a month, a year, some dates, or a trip.")
                                    OutlinedButton(onClick = { adding = true }) { Text("New budget") }
                                }
                            }
                        }
                        else -> {
                            items(state.items, key = { it.id }) { budget ->
                                BudgetCard(budget, onEdit = { editing = budget }, onDelete = { deleting = budget })
                            }
                            if (state.loadingMore) {
                                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("New budget") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }

    if (adding || editing != null) {
        val budget = editing
        BudgetFormSheet(
            budget = budget,
            categories = state.categories,
            onSave = { input -> model.save(budget, input) },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    deleting?.let { budget ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete budget?") },
            text = { Text("“${budget.name}” will be deleted. Its transactions stay, but are no longer linked to a budget.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    model.delete(budget)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A budget: what it counts, how much of it is spent, and what is left. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetCard(budget: Budget, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalChartColors.current
    val health = budgetHealth(budget.percentageUsed)
    val barColor = when (health) {
        BudgetHealth.Over -> colors.negative
        BudgetHealth.Warning -> WARNING
        BudgetHealth.Ok -> colors.blue
    }
    val inactive = budget.status == Status.Inactive
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).alpha(if (inactive) 0.7f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(budget.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                periodName(budget.period)?.let { Badge(it, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
                if (inactive) {
                    Spacer(Modifier.width(4.dp))
                    Badge("Inactive", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More for ${budget.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (budget.description.isNotBlank()) {
                    Text(budget.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (budget.merchant != null || budget.categories.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        budget.merchant?.let { Tag("Spending at “$it”") }
                        budget.categories.forEach { Tag(it.name, parseHexColor(it.color)) }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Money.format(budget.totalSpent),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (health == BudgetHealth.Over) colors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "of ${Money.format(budget.totalAmount)}${periodSuffix(budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                PillBar(
                    (budget.percentageUsed / 100).toFloat(),
                    barColor,
                    Modifier.semantics { contentDescription = "${Money.percent(budget.percentageUsed)} used" },
                )
                Row {
                    Text(
                        if (budget.totalRemaining >= 0) "${Money.format(budget.totalRemaining)} left" else "${Money.format(abs(budget.totalRemaining))} over",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (budget.totalRemaining < 0) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (budget.totalRemaining < 0) FontWeight.Medium else null,
                    )
                    Text(
                        Money.percent(budget.percentageUsed),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = when (health) {
                            BudgetHealth.Over -> colors.negative
                            BudgetHealth.Warning -> WARNING
                            BudgetHealth.Ok -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Tag(text: String, dot: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            ColorDot(dot, size = 8.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
