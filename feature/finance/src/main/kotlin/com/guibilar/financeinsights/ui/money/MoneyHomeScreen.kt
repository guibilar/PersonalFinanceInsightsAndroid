package com.guibilar.financeinsights.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.ui.AccountsRoute
import com.guibilar.financeinsights.ui.BudgetsRoute
import com.guibilar.financeinsights.ui.CategoriesRoute
import com.guibilar.financeinsights.ui.DashboardRoute
import com.guibilar.financeinsights.ui.MoneyLink
import com.guibilar.financeinsights.ui.NetWorthRoute
import com.guibilar.financeinsights.ui.ReviewRoute
import com.guibilar.financeinsights.ui.RulesRoute
import com.guibilar.financeinsights.ui.SubscriptionsRoute
import com.guibilar.financeinsights.ui.TagsRoute
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/**
 * The Money module's home: the four headline tiles, then a row for each finance page, in the web's groups (Overview,
 * Spending, Setup). [open] takes the route of the page to open; [openImport] opens the transactions with the import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyHomeScreen(
    model: DashboardViewModel,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    open: (Any) -> Unit,
    openImport: () -> Unit,
) {
    val state by model.state.collectAsState()
    // Back from a finance page or the background: the figures reload.
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val attention = state.attention().size
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = { FitText("Money", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { IconButton(onClick = openImport) { Icon(Icons.Outlined.UploadFile, "Import statements") } },
        )
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
            // On a tablet, a centred column rather than rows stretched across the screen.
            ReadableColumn {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MoneyGlance(
                        state,
                        open = { link ->
                            when (link) {
                                MoneyLink.NetWorth -> open(NetWorthRoute)
                                MoneyLink.Budgets -> open(BudgetsRoute)
                                MoneyLink.Subscriptions -> open(SubscriptionsRoute)
                                MoneyLink.Rules -> open(RulesRoute)
                                MoneyLink.Import -> openImport()
                            }
                        },
                        modifier = Modifier.padding(16.dp),
                        // The dashboard holds the list; its row says how many.
                        attention = false,
                    )
                    Group("Overview")
                    Entry(
                        Icons.Outlined.Dashboard,
                        "Dashboard",
                        when (attention) {
                            0 -> "The month at a glance, with charts"
                            1 -> "1 item needs attention"
                            else -> "$attention items need attention"
                        },
                        supportingColor = if (attention > 0) LocalChartColors.current.negative else null,
                    ) { open(DashboardRoute) }
                    Entry(Icons.AutoMirrored.Outlined.ShowChart, "Net worth", "What the accounts hold, less what the cards owe") { open(NetWorthRoute) }
                    Entry(Icons.Outlined.CalendarMonth, "Review", "A month or a year against the one before") { open(ReviewRoute()) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Group("Spending")
                    Entry(Icons.AutoMirrored.Outlined.ReceiptLong, "Transactions", "Search, filter, correct and add") { open(TransactionsRoute()) }
                    Entry(Icons.Outlined.AccountBalanceWallet, "Budgets", "Limits on what is spent") { open(BudgetsRoute) }
                    Entry(Icons.Outlined.Autorenew, "Subscriptions", "Payments that come again and again, and what they cost") { open(SubscriptionsRoute) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Group("Setup")
                    Entry(Icons.Outlined.AccountBalance, "Accounts", "The bank accounts statements are imported into") { open(AccountsRoute) }
                    Entry(Icons.Outlined.Palette, "Categories", "Names and colours, everywhere they show") { open(CategoriesRoute) }
                    Entry(Icons.Outlined.Tag, "Tags", "Your own labels across categories") { open(TagsRoute) }
                    Entry(Icons.AutoMirrored.Outlined.Rule, "Rules", "What sets each transaction's category, and what it counts as") { open(RulesRoute) }
                }
            }
        }
    }
}

/** A group's name over its entries. */
@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, supportingColor: Color? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = supportingColor ?: Color.Unspecified) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
