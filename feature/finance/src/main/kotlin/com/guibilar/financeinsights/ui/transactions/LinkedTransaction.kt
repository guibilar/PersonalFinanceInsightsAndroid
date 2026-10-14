package com.guibilar.financeinsights.ui.transactions

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// A link to one transaction (TransactionsRoute.transactionId, or "View" after an add): the message when the list
// cannot show it, and how long its row stays highlighted.

/** How long the linked row stays highlighted, as on the web (T33). */
internal const val LINK_HIGHLIGHT_MS = 3_000

/** The rows come after these items of the list: the search (with the chips and the link message) and the totals. */
internal const val LIST_HEADER_ITEMS = 2

/**
 * True when the phone's "Remove animations" setting is on (animator duration scale 0). The highlight then does not
 * fade: it shows at full strength for its time, then goes.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Why the linked transaction is not in the list, over the normal list; a deleted one offers the deleted list. */
@Composable
internal fun LinkNoticeCard(notice: LinkNotice, onClose: () -> Unit, onShowDeleted: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notice.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (notice == LinkNotice.Deleted) {
                    TextButton(onClick = onShowDeleted) { Text("Show deleted transactions") }
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close the message") }
        }
    }
}
