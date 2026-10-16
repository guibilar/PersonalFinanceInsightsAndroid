package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentSuggestions
import com.guibilar.financeinsights.core.documents.TransactionMatch
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.logic.Money
import java.time.LocalDate

/** "Costco Wholesale · Oct 3, 2026 · $33.05". */
fun receiptLine(s: DocumentSuggestions): String = listOfNotNull(
    s.merchant,
    s.date?.let { runCatching { formatDay(LocalDate.parse(it)) }.getOrNull() },
    s.total?.let { Money.format(it) },
).joinToString(" · ")

/**
 * "Hestia read this" (T100): what the text of a receipt says, the transactions of that amount near that day (Link), "Add
 * as a transaction" (the add sheet filled in; the receipt links to the new one), the kind, and the warranty end as the
 * expiry. Nothing for a read-only user but the line and the matches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionsCard(
    document: Document,
    suggestions: DocumentSuggestions,
    canWrite: Boolean,
    busy: Boolean,
    link: (TransactionMatch) -> Unit,
    apply: (kind: DocumentKind?, expiresOn: String?) -> Unit,
    addTransaction: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("  Hestia read this", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            receiptLine(suggestions).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
            if (suggestions.transactions.isNotEmpty()) {
                Text(
                    if (suggestions.transactions.size == 1) "A transaction matches" else "Transactions match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                suggestions.transactions.forEach { match ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(match.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    runCatching { formatDay(LocalDate.parse(match.date.take(10))) }.getOrNull(),
                                    match.accountName.ifEmpty { null },
                                    Money.format(kotlin.math.abs(match.amount)),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            match.linked -> Text("Linked", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            canWrite -> FilledTonalButton(onClick = { link(match) }, enabled = !busy) { Text("Link") }
                        }
                    }
                }
            } else if (!suggestions.transactionsComplete) {
                Text("Finance did not answer, so the matching transactions are not here.", style = MaterialTheme.typography.bodySmall)
            }
            if (canWrite) {
                val anyLinked = suggestions.transactions.any { it.linked } || document.transactionLinks.isNotEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (suggestions.total != null && !anyLinked && addTransaction != null) {
                        OutlinedButton(onClick = addTransaction, enabled = !busy) { Text("Add as a transaction") }
                    }
                    suggestions.kind?.let { kind ->
                        OutlinedButton(onClick = { apply(kind, null) }, enabled = !busy) { Text("Mark as ${kind.label}") }
                    }
                    suggestions.warrantyUntil?.takeIf { it != document.expiresOn }?.let { until ->
                        val day = runCatching { formatDay(LocalDate.parse(until)) }.getOrDefault(until)
                        OutlinedButton(onClick = { apply(null, until) }, enabled = !busy) { Text("Warranty until $day: set as expiry") }
                    }
                }
            }
        }
    }
}
