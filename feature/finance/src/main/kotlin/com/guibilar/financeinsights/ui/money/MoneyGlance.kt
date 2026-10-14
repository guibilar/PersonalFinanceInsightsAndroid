package com.guibilar.financeinsights.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.ui.NetWorthRoute
import com.guibilar.financeinsights.ui.BudgetsRoute
import com.guibilar.financeinsights.ui.dashboard.DashboardState
import com.guibilar.financeinsights.ui.dashboard.HeadlineTiles
import com.guibilar.financeinsights.ui.dashboard.NeedsAttention

/**
 * The Money module at a glance: the dashboard's four headline tiles and its "Needs attention" list (the server's
 * Money alerts, the same items as on Today). The Money home shows it; each tile and item opens its page: [open] takes
 * the route of a finance page, [openImport] opens the transactions with the import (with an account, when an item
 * names one), and [retry] checks the alerts again.
 */
@Composable
fun MoneyGlance(
    state: DashboardState,
    open: (Any) -> Unit,
    openImport: (account: String?) -> Unit,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeadlineTiles(state, openNetWorth = { open(NetWorthRoute) }, openBudgets = { open(BudgetsRoute) })
        NeedsAttention(state.alerts, retry, open, openImport)
    }
}
