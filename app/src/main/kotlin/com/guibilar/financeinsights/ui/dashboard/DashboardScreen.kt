package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.AttentionKind
import com.guibilar.financeinsights.core.logic.attentionItems
import com.guibilar.financeinsights.core.logic.investments
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.ui.AccountInfo
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.common.AccountMenuButton
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.transactions.TransactionRow
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
    openNetWorth: () -> Unit = {},
    openAccounts: () -> Unit = {},
    /** The transactions, with the import open. */
    openImport: () -> Unit = {},
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
                    Text("$monthName at a glance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                IconButton(onClick = openImport) { Icon(Icons.Outlined.UploadFile, "Import statements") }
                AccountMenuButton(account, onSignOut)
            },
        )
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { HeadlineTiles(state, openNetWorth, openBudgets) }
                val attention = if (state.attentionLoaded) {
                    attentionItems(state.unsorted, state.budgets.valueOrNull?.items.orEmpty(), state.recurring, state.accounts, state.today)
                } else emptyList()
                if (attention.isNotEmpty()) {
                    item {
                        NeedsAttention(attention) { kind ->
                            when (kind) {
                                AttentionKind.Budget -> openBudgets()
                                AttentionKind.Statement -> openImport()
                                AttentionKind.Price -> openSubscriptions()
                                AttentionKind.Unsorted -> openRules()
                            }
                        }
                    }
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
                item { ByCategory(state, monthName, model, openTransactions) }
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
                    SectionCard("Income and spending", subtitle = "Per month, this year") {
                        Loaded(state.cashFlow, model::retryCashFlow, lines = 6) { report ->
                            if (report.months.isEmpty()) EmptyState("Nothing recorded this year yet.")
                            else CashFlow(report)
                        }
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
                val held = investments(state.accounts)
                if (held.isNotEmpty()) {
                    item {
                        SectionCard(
                            "Investments",
                            subtitle = "What they hold, and this year's room",
                            action = {
                                TextButton(onClick = openAccounts) {
                                    Text("Accounts")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                                }
                            },
                        ) { Investments(held) }
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
                            if (page.items.isEmpty()) EmptyState("No transactions yet. Import a statement to start.")
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
internal fun <T> Loaded(state: LoadState<T>, retry: () -> Unit, lines: Int = 4, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> Skeleton(lines = lines)
        is LoadState.Failed -> ErrorState(state.message, retry)
        is LoadState.Loaded -> content(state.value)
    }
}

internal fun placeholder(state: LoadState<*>) = if (state is LoadState.Failed) "—" else "…"

internal fun failedHint(state: LoadState<*>, failed: String) = if (state is LoadState.Failed) failed else null

@Composable
internal fun AmountRow(color: Color, label: String, amount: String, onClick: (() -> Unit)? = null, trailing: String? = null) {
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

@Composable
private fun RecentTransactions(transactions: List<Transaction>) {
    Column { transactions.forEach { TransactionRow(it, horizontalPadding = 0.dp) } }
}
