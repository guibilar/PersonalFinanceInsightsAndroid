package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Savings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.ui.charts.ChartSeries
import com.guibilar.financeinsights.ui.charts.GroupedBarChart
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/** Income against spending and what was left over per month, averages first; savings change when there is any. */
@Composable
internal fun CashFlow(report: CashFlowReport) {
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
                    // Hidden in privacy mode, the cell says so rather than going blank.
                    Money.compact(value).ifEmpty { Money.HIDDEN },
                    Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = if (i == 2 && value < 0) colors.negative else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
