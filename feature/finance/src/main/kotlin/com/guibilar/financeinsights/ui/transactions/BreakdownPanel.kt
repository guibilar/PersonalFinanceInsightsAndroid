package com.guibilar.financeinsights.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.model.Breakdown
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.BreakdownPeriod
import com.guibilar.financeinsights.ui.charts.PeriodCharts
import com.guibilar.financeinsights.ui.common.ChoiceRow
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor

/** Whether a breakdown is about money in: nothing was spent, but something came in. */
private fun Breakdown.isIncome() = spending == 0.0 && income > 0

/** The totals of what the criteria pick, and on request how they spread over time and where the money went. */
@Composable
fun BreakdownPanel(state: TransactionsState, model: TransactionsViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        // At large text sizes the totals take the full width, the button below: side by side they broke mid-number.
        val padding = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        if (isLargeText) {
            Column(padding) {
                TotalsSummary(state, model, Modifier.fillMaxWidth())
                OverTimeButton(state, model)
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically) {
                TotalsSummary(state, model, Modifier.weight(1f))
                OverTimeButton(state, model)
            }
        }
        AnimatedVisibility(state.breakdownOpen) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OverTime(state, model)
            }
        }
    }
}

/** How many transactions, spent and received, for the totals strip. */
@Composable
private fun TotalsSummary(state: TransactionsState, model: TransactionsViewModel, modifier: Modifier) {
    val colors = LocalChartColors.current
    val breakdown = state.breakdown.valueOrNull
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            breakdown != null -> {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(breakdown.count.toString()) }
                        append(if (breakdown.count == 1) " transaction" else " transactions")
                        if (breakdown.spending != 0.0 || breakdown.income == 0.0) {
                            append(" · ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Money.format(breakdown.spending)) }
                            append(" spent")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val extra = buildAnnotatedString {
                    if (breakdown.refunds > 0) append("${Money.format(breakdown.refunds)} refunded")
                    if (breakdown.income > 0) {
                        if (length > 0) append(" · ")
                        withStyle(SpanStyle(color = colors.positive, fontWeight = FontWeight.SemiBold)) { append(Money.format(breakdown.income)) }
                        append(" received")
                    }
                }
                if (extra.isNotEmpty()) {
                    Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.breakdown is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Could not total these.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = model::retryBreakdown) { Text("Retry") }
            }
            else -> Text("Totalling…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverTimeButton(state: TransactionsState, model: TransactionsViewModel) {
    OutlinedButton(onClick = model::toggleBreakdown) {
        Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (state.breakdownOpen) "Hide" else "Over time")
    }
}

@Composable
private fun OverTime(state: TransactionsState, model: TransactionsViewModel) {
    val colors = LocalChartColors.current
    val loaded = state.breakdown.valueOrNull
    if (state.breakdown is LoadState.Failed && loaded == null) {
        ErrorState((state.breakdown as LoadState.Failed).message, model::retryBreakdown)
        return
    }
    // The interval the API used: years of weeks come back as months.
    val interval = loaded?.interval ?: state.interval
    ChoiceRow(BreakdownInterval.entries, interval, { it.name }, model::setInterval)
    if (loaded == null) {
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.alpha(if (state.breakdownLoading) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loaded.count == 0) {
            Text("No transactions match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val income = loaded.isIncome()
        val amount = { p: BreakdownPeriod -> if (income) p.income else p.spending }
        val color = state.criteria.categoryId?.let { id -> state.categories.firstOrNull { it.id == id } }?.let { parseHexColor(it.color) }
            ?: if (income) colors.income else colors.blue
        val average = if (income) loaded.averageIncome else loaded.averageSpending
        val highest = loaded.periods.maxByOrNull(amount)?.takeIf { amount(it) > 0 }
        Text(
            buildString {
                append("Average ${Money.format(average)} per ${interval.one} · over ${loaded.averagedPeriods} ")
                append(if (loaded.averagedPeriods == 1) interval.one else interval.many)
                if (highest != null) append(" · Highest ${highest.label}, ${Money.format(amount(highest))}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val selected = state.selectedPeriod
        PeriodCharts(
            labels = loaded.periods.map { it.label.substringBefore(' ').takeIf { _ -> interval != BreakdownInterval.Week } ?: it.label },
            values = loaded.periods.map(amount),
            cumulative = loaded.periods.map { if (income) it.cumulativeIncome else it.cumulativeSpending },
            color = color,
            average = average,
            selected = selected,
            onSelect = model::selectPeriod,
            description = "${if (income) "Income" else "Spending"} per ${interval.one}, with the running total below. Tap a bar for that ${interval.one}.",
        )
        val period = selected?.let { loaded.periods.getOrNull(it) }
        if (period != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(period.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${Money.format(amount(period))} · ${period.count} ${if (period.count == 1) "transaction" else "transactions"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { model.showPeriod(period) }) { Text("Show these") }
            }
        } else {
            Text("Tap a bar to see that ${interval.one}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!income) Merchants(state, model, color)
    }
}

/** Where the spending went, largest first, a page at a time. */
@Composable
private fun Merchants(state: TransactionsState, model: TransactionsViewModel, color: Color) {
    val merchants = state.merchants
    val period = state.period
    // A selected bar's merchants are on their way: the heading says which, with a bar until they come.
    if (period != null && merchants.isEmpty()) {
        if (state.loadingMerchants) {
            Spacer(Modifier.height(4.dp))
            Text("Where it went in ${period.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (state.merchantsFailed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Could not load where it went in ${period.label}.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = model::loadMoreMerchants) { Text("Retry") }
            }
        } else {
            Text("No spending in ${period.label}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (merchants.size <= 1 && period == null) return
    if (merchants.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Text(
        if (period != null) "Where it went in ${period.label}" else "Where it went",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    val max = merchants.maxOf { it.spending }.coerceAtLeast(0.01)
    merchants.forEach { merchant ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(merchant.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${merchant.count}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(Money.format(merchant.spending), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            PillBar((merchant.spending / max).toFloat(), color)
        }
    }
    val left = state.merchantsLeft
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (left > 0) {
            OutlinedButton(onClick = model::loadMoreMerchants, enabled = !state.loadingMerchants) {
                Text(if (state.loadingMerchants) "Loading…" else "Show ${minOf(left, TransactionsViewModel.MERCHANTS_PAGE_SIZE)} more")
            }
        }
        // For a bar picked, its own merchants beyond the first page; otherwise the whole range's.
        val expanded = if (state.period != null) state.periodMerchants.size > TransactionsViewModel.MERCHANTS_PAGE_SIZE
        else state.moreMerchants.isNotEmpty()
        if (expanded) TextButton(onClick = model::showFewerMerchants) { Text("Show fewer") }
    }
    Text(
        if (state.merchantsFailed) "Could not load more. Try again." else "${merchants.size} of ${merchants.size + left} places",
        style = MaterialTheme.typography.bodySmall,
        color = if (state.merchantsFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
