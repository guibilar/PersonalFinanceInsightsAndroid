package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import com.guibilar.financeinsights.ui.common.budgetWarningText
import com.guibilar.financeinsights.core.model.SpendingForecast
import com.guibilar.financeinsights.core.logic.shortDay
import com.guibilar.financeinsights.core.logic.Change
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.BudgetHealth
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.averageCaption
import com.guibilar.financeinsights.core.logic.budgetHealth
import com.guibilar.financeinsights.core.logic.budgetsOverLimit
import com.guibilar.financeinsights.core.logic.categoryAverages
import com.guibilar.financeinsights.core.logic.categoryShares
import com.guibilar.financeinsights.core.logic.monthOf
import com.guibilar.financeinsights.core.logic.monthlyAverage
import com.guibilar.financeinsights.core.logic.spentHint
import com.guibilar.financeinsights.core.logic.spentThisMonth
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.CategorizedReport
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.charts.ChartSeries
import com.guibilar.financeinsights.ui.charts.GroupedBarChart
import com.guibilar.financeinsights.ui.charts.StackedBarChart
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.BUDGET_WARNING
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.StatTile
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import com.guibilar.financeinsights.ui.transactions.TransactionRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Opens the transactions on a category, all of its history or one month of it week by week, with "Over time" open. */
fun categoryLink(categoryId: String, month: YearMonth? = null) = TransactionsRoute(
    categoryId = categoryId,
    startDate = month?.atDay(1)?.toString(),
    endDate = month?.atEndOfMonth()?.toString(),
    overTime = true,
    interval = if (month != null) BreakdownInterval.Week else null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    model: DashboardViewModel,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    openTransactions: (TransactionsRoute) -> Unit,
    openBudgets: () -> Unit = {},
    openRules: () -> Unit = {},
    openReview: () -> Unit = {},
    openSubscriptions: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    // Back from the background on a new day: today moves on.
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val monthName = state.today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = {
                Column {
                    Text("Dashboard", fontWeight = FontWeight.SemiBold)
                    Text("Your spending in $monthName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = { AccountMenuButton(account, onSignOut) },
        )
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { HeadlineTiles(state) }
                if (state.unsorted > 0) {
                    item { UnsortedNudge(state.unsorted, openRules) }
                }
                item {
                    SectionCard(
                        "Where the month is heading",
                        subtitle = "If $monthName goes as usual",
                        action = {
                            TextButton(onClick = openReview) {
                                Text("Last month")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                            }
                        },
                    ) {
                        Loaded(state.forecast, model::retryForecast) { forecast -> MonthForecast(forecast, openSubscriptions) }
                    }
                }
                item {
                    SectionCard("Monthly spending", subtitle = "By category, this year") {
                        Loaded(state.year, model::retryYear, lines = 6) { report ->
                            if (report.monthlyData.isEmpty()) EmptyState("No spending recorded this year yet.")
                            else MonthlySpending(report, state.today, openTransactions)
                        }
                    }
                }
                item {
                    SectionCard("This month by category", subtitle = monthName) {
                        Loaded(state.month, model::retryMonth) { report ->
                            if (report.monthlyData.isEmpty()) EmptyState("No spending this month yet.")
                            else CategoryBreakdown(report, state.today, openTransactions)
                        }
                    }
                }
                item {
                    SectionCard("Average by category", subtitle = "A typical month, against this one") {
                        Loaded(state.year, model::retryYear) { report ->
                            if (report.monthlyData.isEmpty()) EmptyState("No spending recorded this year yet.")
                            else CategoryAverages(report, state.today)
                        }
                    }
                }
                item {
                    SectionCard("Income and spending", subtitle = "Per month, this year") {
                        Loaded(state.cashFlow, model::retryCashFlow, lines = 6) { report ->
                            if (report.months.isEmpty()) EmptyState("Nothing recorded this year yet.")
                            else CashFlow(report)
                        }
                    }
                }
                item {
                    SectionCard(
                        "Budgets at a glance",
                        action = {
                            TextButton(onClick = openBudgets) {
                                Text("Manage")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                            }
                        },
                    ) {
                        Loaded(state.budgets, model::retryBudgets) { page ->
                            if (page.items.isEmpty()) EmptyState("No active budgets yet.")
                            else BudgetOverview(page.items)
                        }
                    }
                }
                item {
                    SectionCard(
                        "Recent transactions",
                        action = {
                            TextButton(onClick = { openTransactions(TransactionsRoute()) }) {
                                Text("View all")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                            }
                        },
                    ) {
                        Loaded(state.recent, model::retryRecent) { page ->
                            if (page.items.isEmpty()) EmptyState("No transactions yet. Import a statement from Transactions.")
                            else RecentTransactions(page.items)
                        }
                    }
                }
            }
        }
    }
}

/** A report's content once it is here, a skeleton while it loads, or what failed with a retry. */
@Composable
private fun <T> Loaded(state: LoadState<T>, retry: () -> Unit, lines: Int = 4, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> Skeleton(lines)
        is LoadState.Failed -> ErrorState(state.message, retry)
        is LoadState.Loaded -> content(state.value)
    }
}

@Composable
private fun HeadlineTiles(state: DashboardState) {
    val colors = LocalChartColors.current
    val month = state.month.valueOrNull
    val year = state.year.valueOrNull
    val budgets = state.budgets.valueOrNull?.items
    val failed = "Could not load this"
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            val hint = month?.let { spentHint(it, state.today) }
            StatTile(
                "Spent this month",
                month?.let { Money.format(spentThisMonth(it)) } ?: placeholder(state.month),
                Icons.Outlined.CalendarMonth,
                m,
                hint = hint?.text ?: failedHint(state.month, failed),
                hintColor = when {
                    hint == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    hint.worse -> colors.negative
                    else -> colors.positive
                },
                hintIcon = hint?.let { if (it.up) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown },
            )
        },
        { m ->
            StatTile(
                "Monthly average",
                year?.let { Money.format(it.averageMonthlyTotal) } ?: placeholder(state.year),
                Icons.AutoMirrored.Outlined.TrendingUp,
                m,
                hint = year?.let { averageCaption(it.monthlyData.map { d -> d.month }, state.today) } ?: failedHint(state.year, failed),
            )
        },
        { m ->
            val top = month?.let { categoryShares(it).firstOrNull() }
            StatTile(
                "Top category",
                if (month != null) top?.category ?: "—" else placeholder(state.month),
                Icons.Outlined.Sell,
                m,
                hint = if (month != null) top?.let { "${Money.format(it.amount)} this month" } ?: "No spending yet" else failedHint(state.month, failed),
            )
        },
        { m ->
            val over = budgets?.let(::budgetsOverLimit)
            StatTile(
                "Budgets",
                when {
                    budgets == null -> placeholder(state.budgets)
                    budgets.isEmpty() -> "None yet"
                    else -> "$over of ${budgets.size} over"
                },
                Icons.Outlined.AccountBalanceWallet,
                m,
                hint = when {
                    budgets == null -> failedHint(state.budgets, failed)
                    budgets.isEmpty() -> "Create one to track a limit"
                    over == 0 -> "All within limits"
                    else -> "Review your budgets"
                },
                hintColor = when {
                    budgets.isNullOrEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                    over == 0 -> colors.positive
                    else -> colors.negative
                },
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Two a row, as tall as the taller one; one a row when the text is large, so amounts aren't cut off.
        val perRow = if (isLargeText) 1 else 2
        tiles.chunked(perRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

private fun placeholder(state: LoadState<*>) = if (state is LoadState.Failed) "—" else "…"

private fun failedHint(state: LoadState<*>, failed: String) = if (state is LoadState.Failed) failed else null

/** Spending per month, stacked by category, with the average over the finished months; categories can be hidden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlySpending(report: CategorizedReport, today: LocalDate, open: (TransactionsRoute) -> Unit) {
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selected by remember(report) { mutableStateOf<Int?>(null) }
    val visible = report.categories.filter { it.name !in hidden }
    val average = monthlyAverage(report, today, visible.map { it.name }.toSet())
    val labels = report.monthlyData.map { it.month.take(3) }
    StackedBarChart(
        labels = labels,
        series = visible.map { category ->
            ChartSeries(category.name, parseHexColor(category.color), report.monthlyData.map { it.categoryValues[category.name] ?: 0.0 })
        },
        average = average,
        selected = selected,
        onSelect = { selected = it },
        description = "Monthly spending by category. Average ${Money.format(average)} a month. Tap a month for its categories.",
    )
    Text(
        "Dashed line: average of ${Money.format(average)} a month · tap a bar for its categories",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        report.categories.forEach { category ->
            val on = category.name !in hidden
            FilterChip(
                selected = on,
                onClick = { hidden = if (on) hidden + category.name else hidden - category.name },
                label = { Text(category.name) },
                leadingIcon = { ColorDot(if (on) parseHexColor(category.color) else Color.Gray) },
            )
        }
    }
    val index = selected
    if (index != null) {
        val month = report.monthlyData[index]
        val yearMonth = monthOf(month.month)
        Spacer(Modifier.height(8.dp))
        Text(month.month, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        visible.mapNotNull { category -> month.categoryValues[category.name]?.takeIf { it != 0.0 }?.let { category to it } }
            .sortedByDescending { it.second }
            .forEach { (category, amount) ->
                AmountRow(
                    color = parseHexColor(category.color),
                    label = category.name,
                    amount = Money.format(amount),
                    onClick = { open(categoryLink(category.id, yearMonth)) },
                )
            }
    }
}

@Composable
private fun AmountRow(color: Color, label: String, amount: String, onClick: (() -> Unit)? = null, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(amount, fontWeight = FontWeight.Medium)
    }
}

/** This month's categories, largest first, each with its share; a tap lists its transactions week by week. */
@Composable
private fun CategoryBreakdown(report: CategorizedReport, today: LocalDate, open: (TransactionsRoute) -> Unit) {
    val byName = report.categories.associateBy { it.name }
    val shares = categoryShares(report)
    if (shares.isEmpty()) {
        EmptyState("No spending this month yet.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shares.forEach { share ->
            val category = byName[share.category]
            val color = category?.let { parseHexColor(it.color) } ?: Color.Gray
            Column(
                Modifier
                    .fillMaxWidth()
                    .let { m -> if (category != null) m.clickable(role = Role.Button) { open(categoryLink(category.id, YearMonth.from(today))) } else m }
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(share.category, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.percent(share.percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(Money.format(share.amount), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                PillBar((share.percent / 100).toFloat(), color)
            }
        }
    }
}

/** Each category's typical month against this one. */
@Composable
private fun CategoryAverages(report: CategorizedReport, today: LocalDate) {
    val colors = LocalChartColors.current
    val byName = report.categories.associateBy { it.name }
    val rows = categoryAverages(report, today)
    Text(
        averageCaption(report.monthlyData.map { it.month }, today),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    rows.forEach { row ->
        val change = row.change
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(byName[row.category]?.let { parseHexColor(it.color) } ?: Color.Gray)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(row.category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.format(row.thisMonth)} this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Money.format(row.average)}/mo", fontWeight = FontWeight.Medium)
                if (change != null) {
                    Text(
                        // No sign on what rounds to 0%: "−0%" read like a drop.
                        "${if (kotlin.math.round(change) == 0.0) "" else if (change > 0) "+" else "−"}${Money.percent(kotlin.math.abs(change))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            kotlin.math.round(change) == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            change > 0 -> colors.negative
                            else -> colors.positive
                        },
                    )
                }
            }
        }
    }
}

/** Income against spending and what was left over per month, averages first; savings change when there is any. */
@Composable
private fun CashFlow(report: CashFlowReport) {
    val colors = LocalChartColors.current
    var table by rememberSaveable { mutableStateOf(false) }
    var selected by remember(report) { mutableStateOf<Int?>(null) }
    val series = buildList {
        add(ChartSeries("Income", colors.income, report.months.map { it.income }))
        add(ChartSeries("Spending", colors.spending, report.months.map { it.spending }))
        add(ChartSeries("Left over", colors.net, report.months.map { it.net }))
        if (report.hasSavingsChange) add(ChartSeries("Savings change", colors.savings, report.months.map { it.savingsChange }))
    }
    val averages = buildList {
        add(Triple("Income", report.averageMonthlyIncome, colors.income))
        add(Triple("Spending", report.averageMonthlySpending, colors.spending))
        add(Triple("Left over", report.averageMonthlyNet, colors.net))
        if (report.hasSavingsChange) add(Triple("Savings change", report.averageMonthlySavingsChange, colors.savings))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        averages.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, amount, color) ->
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(color, size = 8.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            Money.format(amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (amount < 0) colors.negative else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val caption = when {
            report.averagesIncludeCurrentMonth -> "this month so far"
            else -> "over ${report.averagedMonths} finished month${if (report.averagedMonths == 1) "" else "s"}"
        }
        Text(
            "Averages per month, $caption. Left over is income minus spending." +
                if (report.hasSavingsChange) " Savings change is what went into your savings accounts, minus what came out." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Chart", "Table").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = table == (index == 1),
                    onClick = { table = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { FitText(label) }
            }
        }
        if (!table) {
            GroupedBarChart(
                labels = report.months.map { it.month.take(3) },
                series = series,
                selected = selected,
                onSelect = { selected = it },
                description = "Income, spending and what was left over, per month. Tap a month for its figures.",
            )
            val index = selected
            if (index != null) {
                Text(report.months[index].month, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                series.forEach { s -> AmountRow(s.color, s.name, Money.format(s.values[index])) }
            }
        } else {
            CashFlowTable(report)
        }
    }
}

@Composable
private fun CashFlowTable(report: CashFlowReport) {
    val colors = LocalChartColors.current
    val headers = buildList {
        add("Income"); add("Spending"); add("Left over")
        if (report.hasSavingsChange) add("Savings")
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Month", Modifier.weight(1.1f), style = MaterialTheme.typography.labelMedium)
        headers.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
    }
    report.months.forEach { month ->
        val values = buildList {
            add(month.income); add(month.spending); add(month.net)
            if (report.hasSavingsChange) add(month.savingsChange)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(month.month.take(3), Modifier.weight(1.1f), fontWeight = FontWeight.Medium)
            values.forEachIndexed { i, value ->
                Text(
                    Money.compact(value),
                    Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = if (i == 2 && value < 0) colors.negative else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The active budgets, fullest first, with how much of each is used. */
@Composable
private fun BudgetOverview(budgets: List<Budget>) {
    val colors = LocalChartColors.current
    budgets.sortedByDescending { it.percentageUsed }.forEach { budget ->
        // The same colours and thresholds as on the Budgets screen.
        val health = budgetHealth(budget.percentageUsed)
        val color = when (health) {
            BudgetHealth.Over -> colors.negative
            BudgetHealth.Warning -> BUDGET_WARNING
            BudgetHealth.Ok -> colors.blue
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(budget.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.percentUsed(budget.percentageUsed)} used",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (health == BudgetHealth.Over) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            PillBar((budget.percentageUsed / 100).toFloat(), color)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Money.format(budget.totalSpent)} of ${Money.format(budget.totalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentTransactions(transactions: List<Transaction>) {
    Column { transactions.forEach { TransactionRow(it, horizontalPadding = 0.dp) } }
}

/** "4 transactions are in Other…": a rule per merchant sorts them, under Rules. */
@Composable
private fun UnsortedNudge(count: Int, openRules: () -> Unit) {
    Card(
        onClick = openRules,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count ${if (count == 1) "transaction is" else "transactions are"} in Other because no rule names " +
                    "${if (count == 1) "its merchant" else "their merchants"}.",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("Sort them", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Where the month is heading: spent, the recurring payments still due, and usual day-to-day for the days left. */
@Composable
private fun MonthForecast(forecast: SpendingForecast, openSubscriptions: () -> Unit) {
    val colors = LocalChartColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Money.format(forecast.projected), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("by the end of the month", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.padding(bottom = 3.dp))
    }
    val usual = forecast.usual
    val change = usual?.let { Change.of(forecast.projected, it) }
    Text(
        if (change == null || usual == null) "No complete month yet to compare with."
        else "${change.than("usual")} (${Money.format(usual)}, the average of the last " +
            "${if (forecast.usualMonths == 1) "month" else "${forecast.usualMonths} months"})",
        style = MaterialTheme.typography.bodySmall,
        color = when {
            change == null -> colors.muted
            change.up -> MaterialTheme.colorScheme.error
            change.text == Change.SAME -> colors.muted
            else -> colors.positive
        },
    )
    Spacer(Modifier.height(10.dp))
    val total = forecast.projected.coerceAtLeast(0.01)
    val due = MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        listOf(forecast.spent to MaterialTheme.colorScheme.primary, forecast.upcomingTotal to due).forEach { (amount, color) ->
            val fraction = (amount / total).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) Box(Modifier.fillMaxHeight().weight(fraction).background(color))
        }
        val rest = (forecast.dayToDay / total).toFloat().coerceIn(0f, 1f)
        if (rest > 0f) Spacer(Modifier.weight(rest))
    }
    Spacer(Modifier.height(8.dp))
    ForecastKey(MaterialTheme.colorScheme.primary, "Spent", forecast.spent)
    ForecastKey(due, "Recurring still due", forecast.upcomingTotal)
    ForecastKey(
        MaterialTheme.colorScheme.surfaceVariant,
        "Day-to-day, ${forecast.daysLeft} ${if (forecast.daysLeft == 1) "day" else "days"} at ${Money.format(forecast.dailyRate)}",
        forecast.dayToDay,
    )
    if (forecast.upcoming.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Still to come", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        forecast.upcoming.take(4).forEach { charge ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(charge.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${shortDay(charge.date)}  ", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text(Money.format(charge.amount), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (forecast.upcoming.size > 4) {
            TextButton(onClick = openSubscriptions) { Text("and ${forecast.upcoming.size - 4} more") }
        }
    }
    if (forecast.budgets.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Budgets at this pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        forecast.budgets.forEach { budget ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(budget.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${Money.format(budget.projected)} of ${Money.format(budget.amount)} (${budget.projectedPercentage.roundToInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        budget.projectedPercentage > 100 -> MaterialTheme.colorScheme.error
                        budget.projectedPercentage > 90 -> budgetWarningText
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ForecastKey(color: Color, label: String, amount: Double) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LocalChartColors.current.muted)
        Text(Money.format(amount), style = MaterialTheme.typography.bodySmall)
    }
}
