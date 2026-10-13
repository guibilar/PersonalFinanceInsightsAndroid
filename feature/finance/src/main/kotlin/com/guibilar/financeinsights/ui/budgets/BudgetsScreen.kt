package com.guibilar.financeinsights.ui.budgets

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.BudgetHealth
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.budgetHealth
import com.guibilar.financeinsights.core.logic.periodName
import com.guibilar.financeinsights.core.logic.periodSuffix
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.BUDGET_WARNING
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.READABLE_WIDTH
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.WIDE_CONTENT_WIDTH
import kotlin.math.roundToInt
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.BudgetForecast
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.budgetWarningText
import com.guibilar.financeinsights.ui.common.rememberFabExpansion
import com.guibilar.financeinsights.ui.common.rememberOpenItem
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlin.math.abs

/** Warning amber: nearly at the limit. */
private val WARNING = BUDGET_WARNING

/** As narrow as a budget's card gets: a tablet fits two or three a row, a phone one. */
private val BUDGET_CARD_MIN_WIDTH = 340.dp

/** A grid item across the whole row (a message, the loading spinner). */
private val fullLine: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    model: BudgetsViewModel,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    /** Back to the Money home; none in previews and tests. */
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    // Saved, so a sheet stays open (with what was typed) when the phone rotates.
    var editing by rememberOpenItem(state.items) { it.id }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberOpenItem(state.items) { it.id }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyGridState()
    ShowMessages(model.messages, snackbar)

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) model.loadMore() }

    val fab = rememberFabExpansion()
    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()).nestedScroll(fab.connection)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Budgets", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { AccountMenuButton(account, onSignOut) },
            )
            // On a tablet the budgets are cards side by side, on a page no wider than can be taken in at a glance.
            ReadableColumn(WIDE_CONTENT_WIDTH) {
                Column {
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = model::search,
                        placeholder = { Placeholder("Search budgets") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (state.search.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.widthIn(max = READABLE_WIDTH).fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "All", Status.Active to "Active", Status.Inactive to "Inactive").forEach { (status, label) ->
                            FilterChip(state.status == status, onClick = { model.setStatus(status) }, label = { Text(label) })
                        }
                    }
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = BUDGET_CARD_MIN_WIDTH),
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().alpha(if (state.loading && state.items.isNotEmpty()) 0.5f else 1f),
                        ) {
                            when {
                                state.loading && state.items.isEmpty() -> item(span = fullLine) { Skeleton(lines = 6, lineHeight = 32.dp) }
                                state.error != null -> item(span = fullLine) { ErrorState(state.error!!, model::refresh) }
                                state.items.isEmpty() -> item(span = fullLine) {
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
                                        BudgetCard(budget, onEdit = { editing = budget }, onDelete = { deleting = budget }, projection = state.projections[budget.id])
                                    }
                                    if (state.loadingMore) {
                                        item(span = fullLine) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                                    } else if (state.moreFailed) {
                                        item(span = fullLine) {
                                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                TextButton(onClick = model::loadMore) { Text("Could not load more. Try again") }
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
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("New budget") },
            expanded = fab.expanded,
            // Collapsed (after a scroll) it shows only the icon: TalkBack still says what it adds.
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { contentDescription = "New budget" },
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
private fun BudgetCard(budget: Budget, onEdit: () -> Unit, onDelete: () -> Unit, projection: BudgetForecast? = null) {
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
                    Modifier.semantics { contentDescription = "${Money.percentUsed(budget.percentageUsed)} used" },
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
                        Money.percentUsed(budget.percentageUsed),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = when (health) {
                            BudgetHealth.Over -> colors.negative
                            BudgetHealth.Warning -> budgetWarningText
                            BudgetHealth.Ok -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (projection != null) {
                    Text(
                        "At this pace, ${Money.format(projection.projected)} (${projection.projectedPercentage.roundToInt()}%) by the end of the " +
                            if (projection.period == BudgetPeriod.Yearly) "year" else "month",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            projection.projectedPercentage > 100 -> colors.negative
                            projection.projectedPercentage > 90 -> budgetWarningText
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
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
