package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.ui.common.isLargeText
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val shortDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** "+$12.50" for money in, "−$12.50" for money out, as on the web. */
fun signedAmount(transaction: Transaction): String =
    (if (transaction.transactionType == TransactionType.Credit) "+" else "−") + Money.format(transaction.amount)

/** One transaction: what, which category and account, when, and how much. */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    /** More than opening it (deleting, restoring), for TalkBack. */
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    val colors = LocalChartColors.current
    val category = parseHexColor(transaction.category.color)
    val credit = transaction.transactionType == TransactionType.Credit
    val date = dayFormat.format(transaction.day)
    // At large text sizes the description gets two lines and this year's dates drop the year, or the amount and
    // date column leaves the description a few letters ("SHOPPE…").
    val large = isLargeText
    val shownDate = if (large && transaction.day.year == LocalDate.now().year) shortDayFormat.format(transaction.day) else date
    val flowNote = transaction.flow.takeIf { it != Flow.Spending }?.label
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = horizontalPadding, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    transaction.description, signedAmount(transaction), date, transaction.category.name,
                    transaction.accountName, flowNote, transaction.budget?.let { "Budget ${it.name}" },
                ).joinToString(", ")
                if (onClick != null) onClick(label = "Open") { onClick(); true }
                if (actions.isNotEmpty()) customActions = actions
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(category.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                transaction.category.name.take(1).uppercase(),
                color = category,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(transaction.description, maxLines = if (large) 2 else 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(transaction.category.name, transaction.accountName).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                signedAmount(transaction),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (credit) colors.positive else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (flowNote != null) {
                    Text(
                        flowNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(shownDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
