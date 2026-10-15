package com.guibilar.financeinsights.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/** One line of a "Needs attention" card: its kind (for the icon), its text, and where a tap goes (null for nowhere). */
data class AttentionItem(val key: String, val kind: String, val text: String, val bad: Boolean = false, val onClick: (() -> Unit)? = null)

/** "Needs attention" icons by alert kind: one list for Today and Money (T77). A kind not in it gets a general icon. */
fun attentionIcon(kind: String): ImageVector = when (kind) {
    "budget-over" -> Icons.Outlined.AccountBalanceWallet
    "statements-not-imported", "statements-behind" -> Icons.Outlined.UploadFile
    "price-changed" -> Icons.Outlined.Autorenew
    "unsorted" -> Icons.Outlined.Sell
    // Documents (T11): a document that expires soon, or that expired.
    "expiring", "expired" -> Icons.Outlined.Description
    // Tasks (T19): one overdue task, or many.
    "overdue", "overdue-many" -> Icons.Outlined.CheckBox
    // Home (T22): a warranty that ends soon.
    "warranty-ending" -> Icons.Outlined.VerifiedUser
    // People (T20): a person to reach out to, a birthday soon, a lent thing not back.
    "reach-out" -> Icons.Outlined.Group
    "birthday" -> Icons.Outlined.Cake
    "loan-overdue" -> Icons.Outlined.Handshake
    // System (T80): a stuck event outbox, the event broker down, document files missing.
    "outbox-stuck" -> Icons.Outlined.HourglassEmpty
    "broker-down" -> Icons.Outlined.CloudOff
    "files-missing" -> Icons.Outlined.FindInPage
    else -> Icons.Outlined.ErrorOutline
}

/**
 * The "Needs attention" card of Today and of the Money pages (T77): a heading, [top] (for example an error with "Try
 * again"), one row per item (an item with a destination has an arrow and is a button), and [bottom] (a note).
 */
@Composable
fun NeedsAttentionCard(
    items: List<AttentionItem>,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
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
            top()
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (item.onClick != null) Modifier.clickable(role = Role.Button, onClick = item.onClick) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        attentionIcon(item.kind),
                        null,
                        Modifier.size(18.dp),
                        tint = if (item.bad) LocalChartColors.current.negative else onContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(item.text, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
                    if (item.onClick != null) Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = onContainer)
                }
            }
            bottom()
        }
    }
}
