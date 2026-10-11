package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.averageCaption
import com.guibilar.financeinsights.core.logic.categoryAverages
import com.guibilar.financeinsights.core.logic.categoryShares
import com.guibilar.financeinsights.core.logic.monthOf
import com.guibilar.financeinsights.core.logic.monthlyAverage
import com.guibilar.financeinsights.core.model.CategorizedReport
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.charts.ChartSeries
import com.guibilar.financeinsights.ui.charts.StackedBarChart
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.YearMonth

/** This month's split by category, or each category against a usual month: one card, two views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ByCategory(state: DashboardState, monthName: String, model: DashboardViewModel, open: (TransactionsRoute) -> Unit) {
    var usual by rememberSaveable { mutableStateOf(false) }
    SectionCard("By category", subtitle = if (usual) "A typical month, against this one" else monthName) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            listOf("This month", "Against usual").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = usual == (index == 1),
                    onClick = { usual = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        if (usual) {
            Loaded(state.year, model::retryYear) { report ->
                if (report.monthlyData.isEmpty()) EmptyState("No spending recorded this year yet.")
                else CategoryAverages(report, state.today)
            }
        } else {
            Loaded(state.month, model::retryMonth) { report ->
                if (report.monthlyData.isEmpty()) EmptyState("No spending this month yet.")
                else CategoryBreakdown(report, state.today, open)
            }
        }
    }
}

/** Spending per month, stacked by category, with the average over the finished months; categories can be hidden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthlySpending(report: CategorizedReport, today: LocalDate, open: (TransactionsRoute) -> Unit) {
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
    if (rows.isEmpty()) {
        Text(
            "No month of the year is over yet to compare with.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
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
