package com.guibilar.financeinsights.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Search (design canvas, "Search"): one field; after a pause, the best hits of each module in sections, with "Show all"
 * for one module. A tap on a hit opens its page through [open] (its web link, which the app maps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(model: SearchViewModel, contentPadding: PaddingValues, open: (String) -> Unit) {
    val state by model.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText("Search", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        ReadableColumn {
            Column {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = model::type,
                    placeholder = { Placeholder("Search everything") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (state.text.isNotEmpty()) IconButton(onClick = { model.type("") }) { Icon(Icons.Outlined.Close, "Clear the search") } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val result = state.result
                    when {
                        state.tooShort -> item(key = "hint") {
                            Note("Type at least $MIN_SEARCH characters. The search looks at transactions, documents (with their text), tasks, home items, people and the journal.")
                        }
                        state.error != null && result == null -> item(key = "error") { ErrorState(state.error!!, model::retry) }
                        result == null -> Unit
                        else -> {
                            state.module?.let { module ->
                                item(key = "only") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Note("Only ${sectionName(module)}.", Modifier.weight(1f))
                                        TextButton(onClick = { model.only(null) }) { Text("Search all modules") }
                                    }
                                }
                            }
                            item(key = "summary") { Note(summary(result, state.found.size)) }
                            if (state.failed.isNotEmpty()) item(key = "failed") {
                                Text(
                                    "${state.failed.joinToString(", ")} could not search.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            state.found.forEach { group ->
                                item(key = "title-${group.module}") { SectionTitle(sectionName(group.module)) }
                                items(group.hits, key = { it.id }) { hit -> HitCard(hit, open) }
                                if (state.module == null && group.total > group.hits.size) {
                                    item(key = "more-${group.module}") {
                                        TextButton(onClick = { model.only(group.module) }) { Text("Show all ${group.total}") }
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
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HitCard(hit: SearchHit, open: (String) -> Unit) {
    val link = hit.link
    Card(
        onClick = { link?.let(open) },
        enabled = link != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(hitIcon(hit.kind), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = listOfNotNull(hit.day?.let(::dayText), hit.text?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            hit.amount?.let { amount ->
                Text(
                    Money.format(amount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The icon of a hit by its kind. */
fun hitIcon(kind: String): ImageVector = when (kind) {
    "transaction" -> Icons.Outlined.Payments
    "document" -> Icons.Outlined.Description
    "task" -> Icons.Outlined.CheckBox
    "item" -> Icons.Outlined.Chair
    "person" -> Icons.Outlined.Person
    "entry" -> Icons.Outlined.Book
    else -> Icons.Outlined.Search
}

/** The section of a module: "Transactions", "Items", … */
fun sectionName(module: String): String = when (module) {
    "Money" -> "Transactions"
    "Home" -> "Items"
    else -> module
}

/** "9 results in 3 modules", "1 result in 1 module", "No results for “x”." */
fun summary(result: SearchResult, modules: Int): String = when {
    result.total == 0 -> "No results for “${result.query}”."
    else -> "${if (result.total == 1) "1 result" else "${result.total} results"} in ${if (modules == 1) "1 module" else "$modules modules"}"
}

/** "Sep 8, 2025". */
fun dayText(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }.getOrDefault(day)
