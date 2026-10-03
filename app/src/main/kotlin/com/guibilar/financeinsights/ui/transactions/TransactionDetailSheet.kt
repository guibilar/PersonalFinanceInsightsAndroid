package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything about one transaction. Editing it comes with the next version; the web app can do it meanwhile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(transaction: Transaction, onDismiss: () -> Unit) {
    val colors = LocalChartColors.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(transaction.description, style = MaterialTheme.typography.titleMedium)
            Text(
                signedAmount(transaction),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.transactionType == TransactionType.Credit) colors.positive else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Detail("Date", DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US).format(transaction.day))
            Detail("Category", transaction.category.name + if (transaction.categoryLocked) " · picked by hand" else "") {
                ColorDot(parseHexColor(transaction.category.color))
                Spacer(Modifier.width(8.dp))
            }
            Detail("Counts as", transaction.flow.label + if (transaction.flowLocked) " · picked by hand" else "")
            Detail("Account", listOfNotNull(transaction.accountName, transaction.accountType?.let(::accountTypeLabel)).joinToString(" · "))
            Detail("Budget", transaction.budget?.name ?: "Not linked")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        leading?.invoke()
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
