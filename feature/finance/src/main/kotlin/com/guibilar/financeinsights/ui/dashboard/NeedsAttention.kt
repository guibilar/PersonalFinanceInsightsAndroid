package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.today.AlertDto
import com.guibilar.financeinsights.core.today.MoneyLink
import com.guibilar.financeinsights.core.today.TodayDestination
import com.guibilar.financeinsights.core.today.TodaySeverity
import com.guibilar.financeinsights.core.today.filledText
import com.guibilar.financeinsights.core.today.todayDestination
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.route
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/**
 * What a Money alert opens from a Money page, through the one link map (`todayDestination`): [open] takes the route of
 * a finance page, [openImport] opens the transactions with the import (with the account that the link names, T63). Null when the item opens nothing: no link, a
 * page of another module (Today opens those), or a link that this app does not know (the Money home is already open).
 */
internal fun alertAction(link: String?, open: (Any) -> Unit, openImport: (account: String?) -> Unit): (() -> Unit)? =
    when (val destination = todayDestination(link)) {
        is TodayDestination.Money -> when (destination.link) {
            MoneyLink.Import -> {
                { openImport(destination.importAccount) }
            }
            MoneyLink.Home -> null
            else -> {
                { open(destination.link.route) }
            }
        }
        is TodayDestination.Transaction -> {
            { open(TransactionsRoute(transactionId = destination.id)) }
        }
        TodayDestination.Documents, is TodayDestination.Document, null -> null
    }

/** Whether the "Needs attention" card shows: not while it loads, and not when all is well after a complete check. */
internal val LoadState<MoneyAlerts>.showsCard: Boolean
    get() = when (this) {
        LoadState.Loading -> false
        is LoadState.Failed -> true
        is LoadState.Loaded -> value.items.isNotEmpty() || value.partial
    }

/** "Needs attention" icons by alert kind, the same as on Today. */
private fun attentionIcon(kind: String): ImageVector = when (kind) {
    "budget-over" -> Icons.Outlined.AccountBalanceWallet
    "statements-not-imported", "statements-behind" -> Icons.Outlined.UploadFile
    "price-changed" -> Icons.Outlined.Autorenew
    "unsorted" -> Icons.Outlined.Sell
    else -> Icons.Outlined.ErrorOutline
}

/**
 * What needs a look in Money, from the server's alerts, each opening where it is dealt with ([alertAction]).
 * Nothing shows while it loads, or when all is well. A failure shows "Try again" ([retry]); a partial check shows a
 * short note under the items.
 */
@Composable
internal fun NeedsAttention(
    alerts: LoadState<MoneyAlerts>,
    retry: () -> Unit,
    open: (Any) -> Unit,
    openImport: (account: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!alerts.showsCard) return
    val items = alerts.valueOrNull?.items.orEmpty()
    val partial = alerts.valueOrNull?.partial == true
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Needs attention",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).semantics { heading() },
            )
            if (alerts is LoadState.Failed) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = onContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(alerts.message, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = retry) { Text("Try again") }
                }
            }
            items.forEach { item -> AlertRow(item, alertAction(item.link, open, openImport)) }
            if (partial) {
                Text(
                    if (items.isEmpty()) "Some checks could not run. Pull down to try again." else "Some checks could not run. This list can be out of date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertRow(item: AlertDto, action: (() -> Unit)?) {
    val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        Modifier.fillMaxWidth()
            .then(if (action != null) Modifier.clickable(role = Role.Button, onClick = action) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            attentionIcon(item.kind),
            null,
            Modifier.size(18.dp),
            tint = if (item.severity == TodaySeverity.Bad) LocalChartColors.current.negative else onContainer,
        )
        Spacer(Modifier.width(12.dp))
        Text(item.filledText, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
        if (action != null) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = onContainer)
    }
}
