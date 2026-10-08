package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.budgetsOverLimit
import com.guibilar.financeinsights.core.logic.leftOverThisMonth
import com.guibilar.financeinsights.core.logic.monthOf
import com.guibilar.financeinsights.core.logic.spentHint
import com.guibilar.financeinsights.core.logic.spentThisMonth
import com.guibilar.financeinsights.core.logic.usualHint
import com.guibilar.financeinsights.ui.common.StatTile
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.settings.sinceLastMonth
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import java.time.YearMonth

@Composable
internal fun HeadlineTiles(state: DashboardState, openNetWorth: () -> Unit, openBudgets: () -> Unit) {
    val colors = LocalChartColors.current
    val month = state.month.valueOrNull
    val year = state.year.valueOrNull
    val cashFlow = state.cashFlow.valueOrNull
    val netWorth = state.netWorth.valueOrNull
    val budgets = state.budgets.valueOrNull?.items
    val failed = "Could not load this"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            // Against a usual month at the same point; against last month so far until a month is finished.
            val hint = month?.let { spent ->
                year?.takeIf { report -> report.monthlyData.any { monthOf(it.month)?.isBefore(YearMonth.from(state.today)) == true } }
                    ?.let { usualHint(spentThisMonth(spent), it.averageMonthlyTotal, state.today) }
                    ?: spentHint(spent, state.today)
            }
            StatTile(
                "Spent this month",
                month?.let { Money.format(spentThisMonth(it)) } ?: placeholder(state.month),
                Icons.Outlined.CalendarMonth,
                m,
                hint = hint?.text ?: failedHint(state.month, failed),
                hintColor = when {
                    hint == null -> muted
                    hint.worse -> colors.negative
                    else -> colors.positive
                },
                hintIcon = hint?.let { if (it.up) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown },
            )
        },
        { m ->
            val left = cashFlow?.let { leftOverThisMonth(it, state.today) }
            StatTile(
                "Left over this month",
                when {
                    cashFlow == null -> placeholder(state.cashFlow)
                    left == null -> "—"
                    else -> Money.format(left.net)
                },
                Icons.Outlined.Savings,
                m,
                hint = when {
                    cashFlow == null -> failedHint(state.cashFlow, failed)
                    left == null -> "No income or spending yet"
                    // Before payday, spending alone isn't a warning.
                    left.income == 0.0 -> "No income yet this month"
                    left.net >= 0 -> "Income less spending so far"
                    else -> "More went out than came in"
                },
                hintColor = when {
                    left == null || left.income == 0.0 -> muted
                    left.net >= 0 -> colors.positive
                    else -> colors.negative
                },
            )
        },
        { m ->
            val change = netWorth?.let(::sinceLastMonth)
            StatTile(
                "Net worth",
                netWorth?.let { Money.format(it.netWorth) } ?: placeholder(state.netWorth),
                Icons.AutoMirrored.Outlined.ShowChart,
                m,
                hint = when {
                    netWorth == null -> failedHint(state.netWorth, failed)
                    netWorth.months.isEmpty() -> "Enter a balance to start"
                    change == null -> null
                    else -> "${if (change >= 0) "+" else "−"}${Money.format(kotlin.math.abs(change))} since last month"
                },
                hintColor = when {
                    change == null || change == 0.0 -> muted
                    change > 0 -> colors.positive
                    else -> colors.negative
                },
                hintIcon = change?.takeIf { it != 0.0 }?.let { if (it > 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown },
                onClick = openNetWorth,
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
                    budgets.isNullOrEmpty() -> muted
                    over == 0 -> colors.positive
                    else -> colors.negative
                },
                onClick = openBudgets,
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
