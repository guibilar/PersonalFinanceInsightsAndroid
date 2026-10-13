package com.guibilar.financeinsights.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.AttentionItem
import com.guibilar.financeinsights.core.logic.AttentionKind
import com.guibilar.financeinsights.core.logic.attentionItems
import com.guibilar.financeinsights.ui.MoneyLink
import com.guibilar.financeinsights.ui.dashboard.DashboardState
import com.guibilar.financeinsights.ui.dashboard.HeadlineTiles
import com.guibilar.financeinsights.ui.dashboard.NeedsAttention

/**
 * The Money module at a glance: the dashboard's four headline tiles and, with [attention], its "Needs attention"
 * list. The Money home shows it; each tile and item opens its page through [open]. Today shows the same figures from
 * the server's Today API (`:core:today`).
 */
@Composable
fun MoneyGlance(state: DashboardState, open: (MoneyLink) -> Unit, modifier: Modifier = Modifier, attention: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeadlineTiles(state, openNetWorth = { open(MoneyLink.NetWorth) }, openBudgets = { open(MoneyLink.Budgets) })
        val items = if (attention) state.attention() else emptyList()
        if (items.isNotEmpty()) NeedsAttention(items) { kind -> open(kind.link) }
    }
}

/** What needs attention, the same rules as the dashboard; empty until all its parts are in. */
fun DashboardState.attention(): List<AttentionItem> =
    if (attentionLoaded) attentionItems(unsorted, budgets.valueOrNull?.items.orEmpty(), recurring, accounts, today) else emptyList()

/** Where an attention item is dealt with. */
val AttentionKind.link: MoneyLink
    get() = when (this) {
        AttentionKind.Budget -> MoneyLink.Budgets
        AttentionKind.Statement -> MoneyLink.Import
        AttentionKind.Price -> MoneyLink.Subscriptions
        AttentionKind.Unsorted -> MoneyLink.Rules
    }
