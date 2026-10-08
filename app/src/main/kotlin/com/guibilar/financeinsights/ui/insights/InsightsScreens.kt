package com.guibilar.financeinsights.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Change
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.RecurringPayment
import com.guibilar.financeinsights.core.model.RecurringStatus
import com.guibilar.financeinsights.core.model.ReviewReport
import com.guibilar.financeinsights.ui.common.ChoiceRow
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.PillBar
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.settings.SettingsScaffold
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.flow.emptyFlow

/**
 * Subscriptions, bills and rent: the payments that come again and again, found in the spending. What they cost, when
 * each is next due, which changed price, and which seem to have stopped. Tapping one lists its transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(model: RecurringViewModel, contentPadding: PaddingValues, onBack: () -> Unit, openTransactions: (String) -> Unit) {
    val state by model.state.collectAsState()
    val refreshing by model.refreshing.collectAsState()
    SettingsScaffold("Subscriptions", onBack, contentPadding, emptyFlow()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                LoadState.Loading -> Box(Modifier.padding(16.dp)) { Skeleton(lines = 6, lineHeight = 28.dp) }
                is LoadState.Failed -> ErrorState(current.message, model::reload)
                is LoadState.Loaded -> {
                    val report = current.value
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Payments that come again and again, found in your spending.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Total("A month", Money.format(report.monthlyTotal))
                                    Total("A year", Money.format(report.yearlyTotal))
                                    Total("Coming", report.coming.size.toString())
                                }
                            }
                        }
                        if (report.coming.isEmpty()) {
                            item {
                                EmptyState(
                                    "No recurring payments found yet. They show up once a merchant has charged about the same " +
                                        "amount a few times on a schedule.",
                                    Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        items(report.coming, key = { "${it.name}:${it.amount}" }) { payment ->
                            PaymentRow(payment) { openTransactions(payment.name) }
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }
                        if (report.stopped.isNotEmpty()) {
                            item {
                                Text(
                                    "Stopped",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                                )
                                Text(
                                    "No charge for more than a period after one was due: cancelled, most likely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            items(report.stopped, key = { "stopped:${it.name}:${it.amount}" }) { payment ->
                                PaymentRow(payment, Modifier.alpha(0.7f)) { openTransactions(payment.name) }
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaymentRow(payment: RecurringPayment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalChartColors.current
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(payment.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                when (payment.status) {
                    RecurringStatus.Late -> Badge("Late or missed", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    RecurringStatus.Stopped -> Badge("Stopped", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    RecurringStatus.Active -> {}
                }
                payment.priceChange?.let { change ->
                    val up = change.to > change.from
                    Badge(
                        "${if (up) "Up" else "Down"} from ${Money.format(change.from)}",
                        if (up) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        if (up) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                payment.category?.let {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(parseHexColor(it.color)))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    listOfNotNull(
                        payment.category?.name,
                        payment.cadence.label,
                        if (payment.status == RecurringStatus.Stopped) "last ${formatDay(payment.lastDate, year = false)}" else "next ${formatDay(payment.nextDate, year = false)}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (payment.variableAmount) "~" else "") + Money.format(payment.amount), fontWeight = FontWeight.SemiBold)
            if (payment.cadence.perYear != 12) {
                Text("${Money.format(payment.monthlyCost)}/month", style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = foreground,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * A month or a year looked back on, against the one before: what went out and came in, the categories that moved
 * most, where the most went, the biggest purchase, and (for a year) each month.
 */
@Composable
fun ReviewScreen(model: ReviewViewModel, contentPadding: PaddingValues, onBack: () -> Unit, openSubscriptions: () -> Unit) {
    val state by model.state.collectAsState()
    SettingsScaffold("Review", onBack, contentPadding, emptyFlow()) {
        when (val current = state) {
            LoadState.Loading -> Box(Modifier.padding(16.dp)) { Skeleton(lines = 8, lineHeight = 28.dp) }
            is LoadState.Failed -> ErrorState(current.message, model::reload)
            is LoadState.Loaded -> ReviewContent(current.value, model, openSubscriptions)
        }
    }
}

@Composable
private fun ReviewContent(review: ReviewReport, model: ReviewViewModel, openSubscriptions: () -> Unit) {
    val colors = LocalChartColors.current
    val isYear = review.month == null
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(listOf(false, true), isYear, { if (it) "Year" else "Month" }, { model.switchTo(it) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previous = model.previous()
                    val next = model.next()
                    IconButton(onClick = { model.go(previous) }, enabled = previous != null) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous ${if (isYear) "year" else "month"}")
                    }
                    Text(review.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { model.go(next) }, enabled = next != null) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next ${if (isYear) "year" else "month"}")
                    }
                }
                if (!review.complete) {
                    Text("${review.label} isn't over yet: these are its figures so far.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
        }
        item {
            SectionCard("Spent", subtitle = review.label) {
                Text(Money.format(review.spending), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Change.of(review.spending, review.previousSpending)?.let { change ->
                    Text(
                        change.than(review.previousLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            change.up -> MaterialTheme.colorScheme.error
                            change.text == Change.SAME -> colors.muted
                            else -> colors.positive
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                val net = review.income - review.spending
                Text(
                    "Came in ${Money.format(review.income)} · " +
                        if (net >= 0) "left over ${Money.format(net)}" else "${Money.format(-net)} more went out than came in",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                Text(
                    "${review.purchases} ${if (review.purchases == 1) "purchase" else "purchases"} · " +
                        "${review.noSpendDays} ${if (review.noSpendDays == 1) "day" else "days"} without one",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
        if (isYear && review.months.isNotEmpty()) {
            item {
                SectionCard("Spent each month") {
                    val max = review.months.maxOf { it.spending }.coerceAtLeast(1.0)
                    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        review.months.forEach { month ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${month.label}: ${Money.format(month.spending)}" },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight((month.spending / max).toFloat().coerceIn(0.01f, 0.85f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(MaterialTheme.colorScheme.primary),
                                )
                                Text(month.label.take(1), style = MaterialTheme.typography.labelSmall, color = colors.muted)
                            }
                        }
                    }
                }
            }
        }
        val movers = review.categories.map { it to it.spending - it.previousSpending }.filter { kotlin.math.abs(it.second) >= 1 }
        if (movers.isNotEmpty()) {
            item {
                SectionCard("What changed most", subtitle = "Against ${review.previousLabel}") {
                    (movers.filter { it.second > 0 }.sortedByDescending { it.second }.take(3) +
                        movers.filter { it.second < 0 }.sortedBy { it.second }.take(3)).forEach { (item, change) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(item.category.name, Modifier.weight(1f))
                            Text(
                                (if (change > 0) "+" else "−") + Money.format(kotlin.math.abs(change)),
                                color = if (change > 0) MaterialTheme.colorScheme.error else colors.positive,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionCard("By category", subtitle = "${review.label}, and ${review.previousLabel} under it") {
                if (review.categories.isEmpty()) EmptyState("No spending in either.")
                val max = review.categories.maxOfOrNull { maxOf(it.spending, it.previousSpending) }?.coerceAtLeast(1.0) ?: 1.0
                review.categories.forEach { item ->
                    val color = parseHexColor(item.category.color)
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(item.category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(item.spending), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text(" · was ${Money.format(item.previousSpending)}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        Spacer(Modifier.height(4.dp))
                        PillBar((item.spending / max).toFloat(), color)
                        Spacer(Modifier.height(2.dp))
                        PillBar((item.previousSpending / max).toFloat(), color.copy(alpha = 0.4f))
                    }
                }
            }
        }
        if (review.topMerchants.isNotEmpty()) {
            item {
                SectionCard("Where the most went") {
                    review.topMerchants.forEachIndexed { index, merchant ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("${index + 1}. ${merchant.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${merchant.count}× ", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            Text(Money.format(merchant.spending), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        review.biggestPurchase?.let { purchase ->
            item {
                SectionCard("Biggest purchase") {
                    Text("${Money.format(purchase.amount)} at ${purchase.description}", fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(formatDay(purchase.date, year = false), purchase.category?.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }
        }
        item {
            SuggestionChip(
                onClick = openSubscriptions,
                label = { Text("${review.recurringCount} recurring payments, ${Money.format(review.recurringMonthlyTotal)} a month") },
            )
        }
    }
}
