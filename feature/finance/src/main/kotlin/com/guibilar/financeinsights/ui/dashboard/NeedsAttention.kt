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
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.AttentionItem
import com.guibilar.financeinsights.core.logic.AttentionKind
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/** What needs a look, each opening where it is dealt with. Only shown when something does. */
@Composable
internal fun NeedsAttention(items: List<AttentionItem>, open: (AttentionKind) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Needs attention",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { open(item.kind) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (item.kind) {
                            AttentionKind.Budget -> Icons.Outlined.AccountBalanceWallet
                            AttentionKind.Statement -> Icons.Outlined.UploadFile
                            AttentionKind.Price -> Icons.Outlined.Autorenew
                            AttentionKind.Unsorted -> Icons.Outlined.Sell
                        },
                        null,
                        Modifier.size(18.dp),
                        tint = if (item.kind == AttentionKind.Budget) LocalChartColors.current.negative else MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}
