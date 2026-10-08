package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.accountTypeLabel
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.NetWorthAccount
import com.guibilar.financeinsights.core.model.NetWorthReport
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.ui.charts.ChartSeries
import com.guibilar.financeinsights.ui.charts.StackedBarChart
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.LocalChartColors


/** Since the end of last month, when that month counted every account counted now; null otherwise. */
fun sinceLastMonth(report: NetWorthReport): Double? {
    val months = report.months
    if (months.size < 2) return null
    val (before, now) = months.takeLast(2)
    return if (before.accountsCounted < now.accountsCounted) null else now.netWorth - before.netWorth
}

/** What everything is worth: today's net worth, assets and card debt, the last twelve months, and each account. */
@Composable
fun NetWorthScreen(model: NetWorthViewModel, contentPadding: PaddingValues, onBack: (() -> Unit)?, openAccounts: () -> Unit) {
    val state by model.state.collectAsState()
    SettingsScaffold("Net worth", onBack, contentPadding, model.messages) {
        when (val current = state) {
            LoadState.Loading -> Box(Modifier.padding(16.dp)) { Skeleton(lines = 6, lineHeight = 28.dp) }
            is LoadState.Failed -> ErrorState(current.message, model::reload)
            is LoadState.Loaded -> NetWorth(current.value, openAccounts)
        }
    }
}

@Composable
private fun NetWorth(report: NetWorthReport, openAccounts: () -> Unit) {
    val colors = LocalChartColors.current
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    if (report.accounts.none { it.balance != null }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No balances entered yet.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Enter what each account holds today (what a card owes) under Accounts. From there, the transactions you " +
                    "import keep it up to date; enter an investment's value now and then.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = openAccounts) { Text("Go to Accounts") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Net worth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(report.netWorth), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                sinceLastMonth(report)?.let { change ->
                    Text(
                        "${if (change >= 0) "+" else "−"}${Money.format(kotlin.math.abs(change))} since last month",
                        color = if (change >= 0) colors.positive else colors.negative,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Figure("Assets", Money.format(report.assets))
                    Figure("Owed on cards", Money.format(report.liabilities))
                }
            }
        }
        if (report.months.size > 1) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    StackedBarChart(
                        labels = report.months.map { it.month.take(3) },
                        series = listOf(ChartSeries("Net worth", colors.net, report.months.map { it.netWorth })),
                        description = "Net worth at the end of each month: " +
                            report.months.joinToString("; ") { "${it.month} ${Money.format(it.netWorth)}" },
                        selected = selected,
                        onSelect = { selected = it },
                    )
                    val month = selected?.let { report.months.getOrNull(it) } ?: report.months.last()
                    Text(
                        "${month.month}: ${Money.format(month.netWorth)} · assets ${Money.format(month.assets)} · owed ${Money.format(month.liabilities)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (report.months.any { it.accountsCounted < report.accounts.count { a -> a.balance != null } }) {
                        Text(
                            "Earlier months leave out accounts whose history starts later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
        items(report.accounts, key = { it.id }) { account -> AccountLine(account, openAccounts) }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccountLine(account: NetWorthAccount, openAccounts: () -> Unit) {
    val colors = LocalChartColors.current
    val kind = listOfNotNull(accountTypeLabel(account.type), account.plan.takeIf { it != RegisteredPlan.None }?.label).joinToString(" · ")
    val balance = account.balance
    val note = account.balanceDate?.let {
        "${if (account.movedSince) "Entered" else "As of"} ${formatDay(it)}${if (account.movedSince) ", moved since" else ""}"
    }
    // The text reads as one line to TalkBack; the button stays a button of its own.
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    account.name, kind,
                    balance?.let { (if (account.isLiability) "owes " else "") + Money.format(it) } ?: "no balance yet", note,
                ).joinToString(", ")
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(account.name, fontWeight = FontWeight.Medium)
                Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (balance != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (account.isLiability) "−" else "") + Money.format(balance),
                        fontWeight = FontWeight.SemiBold,
                        color = if (account.isLiability) colors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (balance == null) TextButton(onClick = openAccounts) { Text("Add its balance") }
    }
}
