package com.guibilar.financeinsights.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Change
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.shortDay
import com.guibilar.financeinsights.core.model.SpendingForecast
import com.guibilar.financeinsights.ui.common.budgetWarningText
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import kotlin.math.roundToInt

/** Where the month is heading: spent, the recurring payments still due, and usual day-to-day for the days left. */
@Composable
internal fun MonthForecast(forecast: SpendingForecast, openSubscriptions: () -> Unit) {
    val colors = LocalChartColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Money.format(forecast.projected), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("by the end of the month", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.padding(bottom = 3.dp))
    }
    val usual = forecast.usual
    val change = usual?.let { Change.of(forecast.projected, it) }
    Text(
        if (change == null || usual == null) "No complete month yet to compare with."
        else "${change.than("usual")} (${Money.format(usual)}, the average of the last " +
            "${if (forecast.usualMonths == 1) "month" else "${forecast.usualMonths} months"})",
        style = MaterialTheme.typography.bodySmall,
        color = when {
            change == null -> colors.muted
            change.up -> MaterialTheme.colorScheme.error
            change.text == Change.SAME -> colors.muted
            else -> colors.positive
        },
    )
    Spacer(Modifier.height(10.dp))
    val total = forecast.projected.coerceAtLeast(0.01)
    val due = MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        listOf(forecast.spent to MaterialTheme.colorScheme.primary, forecast.upcomingTotal to due).forEach { (amount, color) ->
            val fraction = (amount / total).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) Box(Modifier.fillMaxHeight().weight(fraction).background(color))
        }
        val rest = (forecast.dayToDay / total).toFloat().coerceIn(0f, 1f)
        if (rest > 0f) Spacer(Modifier.weight(rest))
    }
    Spacer(Modifier.height(8.dp))
    ForecastKey(MaterialTheme.colorScheme.primary, "Spent", forecast.spent)
    ForecastKey(due, "Recurring still due", forecast.upcomingTotal)
    ForecastKey(
        MaterialTheme.colorScheme.surfaceVariant,
        "Day-to-day, ${forecast.daysLeft} ${if (forecast.daysLeft == 1) "day" else "days"} at ${Money.format(forecast.dailyRate)}",
        forecast.dayToDay,
    )
    if (forecast.upcoming.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Still to come", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        forecast.upcoming.take(4).forEach { charge ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(charge.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${shortDay(charge.date)}  ", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text(Money.format(charge.amount), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (forecast.upcoming.size > 4) {
            TextButton(onClick = openSubscriptions) { Text("and ${forecast.upcoming.size - 4} more") }
        }
    }
    if (forecast.budgets.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Budgets at this pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        forecast.budgets.forEach { budget ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(budget.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${Money.format(budget.projected)} of ${Money.format(budget.amount)} (${budget.projectedPercentage.roundToInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        budget.projectedPercentage > 100 -> MaterialTheme.colorScheme.error
                        budget.projectedPercentage > 90 -> budgetWarningText
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ForecastKey(color: Color, label: String, amount: Double) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LocalChartColors.current.muted)
        Text(Money.format(amount), style = MaterialTheme.typography.bodySmall)
    }
}
