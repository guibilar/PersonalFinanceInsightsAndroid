package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.investmentLine
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/** Each investment or registered account: what it holds, this year's contributions and the room left. */
@Composable
internal fun Investments(accounts: List<Account>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        accounts.forEach { account ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (account.plan != RegisteredPlan.None) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            account.plan.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(account.balance?.let { Money.format(it.amount) } ?: "—", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    investmentLine(account),
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((account.roomLeft ?: 0.0) < 0) LocalChartColors.current.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
