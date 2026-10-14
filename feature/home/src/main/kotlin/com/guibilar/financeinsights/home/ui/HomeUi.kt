package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.Warranty
import com.guibilar.financeinsights.core.home.itemSubtitle
import com.guibilar.financeinsights.core.home.roomNameError
import com.guibilar.financeinsights.core.home.warrantyBadge
import com.guibilar.financeinsights.core.home.warrantyOf
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import kotlinx.coroutines.launch
import java.time.LocalDate

// What the Home pages share: the section titles, the item rows, the small badges, the name dialog, a picker sheet,
// and a day field that can be cleared.

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A card of rows, as the other modules show their lists. */
@Composable
internal fun RowsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { content() }
}

/** A small label in a coloured box: "6 days", "Warranty ends in 17 days". [strong] uses the warning colour. */
@Composable
internal fun Badge(text: String, strong: Boolean) {
    val colors = LocalChartColors.current
    val color = if (strong) colors.spending else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The colour of a warranty line: the warning colour when it ends soon, the error colour when it ended. */
@Composable
internal fun warrantyColor(warranty: Warranty): Color = when (warranty) {
    is Warranty.EndsSoon -> LocalChartColors.current.spending
    is Warranty.Ended -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Items in one card: the name, the brand and kind, the value, and the warranty when it needs a look. */
@Composable
internal fun ItemsCard(items: List<ItemSummary>, today: LocalDate, open: (ItemSummary) -> Unit, withRoom: Boolean = false) {
    RowsCard {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
            ItemRow(item, today, withRoom) { open(item) }
        }
    }
}

@Composable
private fun ItemRow(item: ItemSummary, today: LocalDate, withRoom: Boolean, open: () -> Unit) {
    val badge = warrantyBadge(warrantyOf(item.warrantyUntil, today))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = open).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = itemSubtitle(item, withRoom)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            badge?.let { Badge(it, strong = true) }
        }
        Spacer(Modifier.width(12.dp))
        Text(Money.format(item.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** A dialog for a name (a new room, a new room name). [save] answers why it can't be saved, or null when done. */
@Composable
internal fun NameDialog(title: String, initial: String, confirm: String, save: suspend (String) -> String?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val problem = roomNameError(name)
                    if (problem != null) {
                        error = problem
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        error = save(name)
                        saving = false
                        if (error == null) onDismiss()
                    }
                },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A day that can also be empty: the calendar field, and a button that clears it. */
@Composable
internal fun OptionalDateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DateField(label, value, onChange = { onChange(it) }, modifier = Modifier.weight(1f))
        if (value != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Clear $label") }
    }
}

/** A picker in a sheet: a search, and the candidates; a tap on one picks it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PickerSheet(
    title: String,
    searchHint: String,
    candidates: LoadState<List<T>>,
    empty: (String) -> String,
    search: (String) -> Unit,
    onDismiss: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    search(it)
                },
                placeholder = { Placeholder(searchHint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when (candidates) {
                LoadState.Loading -> Skeleton(lines = 5, lineHeight = 28.dp, modifier = Modifier.padding(vertical = 8.dp))
                is LoadState.Failed -> ErrorState(candidates.message, onRetry = { search(text) })
                is LoadState.Loaded -> if (candidates.value.isEmpty()) {
                    Text(
                        empty(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(candidates.value) { row(it) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A row of a picker: a title, a line under it, and an amount. */
@Composable
internal fun CandidateRow(title: String, subtitle: String, amount: String?, enabled: Boolean, pick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = pick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        amount?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
    }
}
