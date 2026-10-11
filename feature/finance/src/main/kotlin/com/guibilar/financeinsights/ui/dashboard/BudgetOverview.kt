package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.BudgetHealth
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.budgetHealth
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.ui.common.BUDGET_WARNING
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.theme.LocalChartColors

/** The active budgets, fullest first, with how much of each is used. */
@Composable
internal fun BudgetOverview(budgets: List<Budget>) {
    val colors = LocalChartColors.current
    budgets.sortedByDescending { it.percentageUsed }.forEach { budget ->
        // The same colours and thresholds as on the Budgets screen.
        val health = budgetHealth(budget.percentageUsed)
        val color = when (health) {
            BudgetHealth.Over -> colors.negative
            BudgetHealth.Warning -> BUDGET_WARNING
            BudgetHealth.Ok -> colors.blue
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(budget.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Money.percentUsed(budget.percentageUsed)} used",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (health == BudgetHealth.Over) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            PillBar((budget.percentageUsed / 100).toFloat(), color)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Money.format(budget.totalSpent)} of ${Money.format(budget.totalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
