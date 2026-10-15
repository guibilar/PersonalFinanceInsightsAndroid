package com.guibilar.financeinsights.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.Skeleton
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The Timeline (design canvas, "Timeline"): what happened in all modules, day by day, newest first. The chips filter
 * by module; "Show earlier" loads the 14 days before; a tap on an entry opens its page through [open] (its web link,
 * which the app maps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    model: TimelineViewModel,
    contentPadding: PaddingValues,
    open: (String) -> Unit,
    /** The time zone of the times of the entries (the phone's). */
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText("Timeline", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        ReadableColumn {
            Column {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TIMELINE_FILTERS.forEach { (label, module) ->
                        FilterChip(selected = state.module == module, onClick = { model.pick(module) }, label = { Text(label) })
                    }
                }
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            state.error != null -> item { ErrorState(state.error!!, model::refresh) }
                            state.pages.isEmpty() -> item { Skeleton(lines = 6, lineHeight = 40.dp) }
                            else -> {
                                if (state.failed.isNotEmpty()) {
                                    item(key = "failed") {
                                        FailedNote(state.failed, model::refresh)
                                    }
                                }
                                if (state.days.isEmpty()) {
                                    item(key = "empty") {
                                        Text(
                                            "Nothing happened in these days.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                state.days.forEach { day ->
                                    item(key = "day-${day.day}") { DayTitle(dayHeading(day.day, state.today ?: day.day)) }
                                    items(day.entries, key = { it.id }) { entry -> EntryRow(entry, zone, open) }
                                }
                                item(key = "earlier") {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (state.pages.lastOrNull()?.hasEarlier == false) {
                                            Text(
                                                "That is everything: nothing happened earlier.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            OutlinedButton(onClick = model::showEarlier, enabled = !state.loading) {
                                                Text(if (state.loading) "Loading…" else "Show earlier")
                                            }
                                        }
                                        if (state.moreFailed) {
                                            Text("Could not load more.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedNote(modules: List<String>, retry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${modules.joinToString(", ")} could not load. The other modules show.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = retry) { Text("Try again") }
        }
    }
}

@Composable
private fun DayTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EntryRow(entry: TimelineEntry, zone: ZoneId, open: (String) -> Unit) {
    val (background, content) = moduleColors(entry.module)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(entryIcon(entry.kind), null, Modifier.size(18.dp), tint = content)
        }
        val link = entry.link
        val body: @Composable () -> Unit = {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    entry.amount?.let { amount ->
                        Text(
                            Money.format(amount),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = if (amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val second = listOfNotNull(entryTime(entry.at, zone), entry.text?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (second.isNotEmpty()) {
                    Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        if (link != null) {
            Card(onClick = { open(link) }, colors = colors, modifier = Modifier.weight(1f)) { body() }
        } else {
            Card(colors = colors, modifier = Modifier.weight(1f)) { body() }
        }
    }
}

/** The icon of an entry by its kind; a kind not in the list gets a general icon. */
fun entryIcon(kind: String): ImageVector = when (kind) {
    "transaction" -> Icons.Outlined.Payments
    "month-review" -> Icons.Outlined.CalendarMonth
    "document-added" -> Icons.Outlined.Description
    "task-done" -> Icons.Outlined.CheckBox
    "item-added" -> Icons.Outlined.Chair
    "contact", "person-added" -> Icons.Outlined.Group
    "loan" -> Icons.Outlined.Person
    "gift-given" -> Icons.Outlined.CardGiftcard
    "entry" -> Icons.Outlined.Book
    else -> Icons.Outlined.History
}

@Composable
private fun moduleColors(module: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (module) {
        "Money" -> scheme.primaryContainer to scheme.onPrimaryContainer
        "Documents", "Home" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        "Journal", "People" -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
}

/** "Today · Sat Oct 10", "Yesterday · Fri Oct 9", "Thu Oct 8", or "Thu Oct 8, 2025" in another year. */
fun dayHeading(day: String, today: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    val now = runCatching { LocalDate.parse(today) }.getOrNull() ?: date
    val plain = date.format(DateTimeFormatter.ofPattern(if (date.year == now.year) "EEE MMM d" else "EEE MMM d, yyyy", Locale.US))
    return when (date) {
        now -> "Today · $plain"
        now.minusDays(1) -> "Yesterday · $plain"
        else -> plain
    }
}

/** "14:20" in [zone], or null for an entry of a whole day. */
fun entryTime(at: String?, zone: ZoneId): String? = try {
    at?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)) }
} catch (_: DateTimeParseException) {
    null
}
