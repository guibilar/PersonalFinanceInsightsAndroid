package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.DateChoice
import com.guibilar.financeinsights.core.logic.DatePreset
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.logic.dateRange
import com.guibilar.financeinsights.core.logic.describeRange
import com.guibilar.financeinsights.core.logic.matchDatePreset
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.theme.parseHexColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The list's filters, changed in place: each change applies at once, as on the web. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(
    state: TransactionsState,
    onDismiss: () -> Unit,
    model: TransactionsViewModel,
) {
    val criteria = state.criteria
    val choice = matchDatePreset(criteria, state.today)
    var pickingRange by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!criteria.isEmpty) TextButton(onClick = model::clearAll) { Text("Clear all") }
            }

            SheetLabel("Dates")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(choice == DateChoice.All, onClick = { model.setDates(DateRange()) }, label = { Text("All dates") })
                DatePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = choice == DateChoice.Preset(preset),
                        onClick = { model.setDates(preset.range(state.today)) },
                        label = { Text(preset.label) },
                    )
                }
                FilterChip(choice == DateChoice.Custom, onClick = { pickingRange = true }, label = { Text("Custom range…") })
            }
            if (choice != DateChoice.All) {
                Text(
                    describeRange(criteria.dateRange()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SheetLabel("Category")
            Picker(
                label = "Category",
                options = listOf<Category?>(null) + state.categories,
                selected = state.categories.firstOrNull { it.id == criteria.categoryId },
                text = { it?.name ?: "All categories" },
                dot = { it?.let { c -> parseHexColor(c.color) } },
                onPick = { model.setCategory(it?.id) },
            )

            SheetLabel("Account")
            Picker(
                label = "Account",
                options = listOf<Account?>(null) + state.accounts,
                selected = state.accounts.firstOrNull { it.id == criteria.accountId },
                text = { it?.let { a -> "${a.name} · ${accountTypeLabel(a.type)}" } ?: "All accounts" },
                onPick = { model.setAccount(it?.id) },
            )

            SheetLabel("Counts as")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(criteria.flow == null, onClick = { model.setFlow(null) }, label = { Text("Anything") })
                Flow.entries.forEach { flow ->
                    FilterChip(criteria.flow == flow, onClick = { model.setFlow(flow) }, label = { Text(flow.label) })
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Show transactions") }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (pickingRange) {
        DateRangeDialog(
            initial = criteria.dateRange(),
            onDismiss = { pickingRange = false },
            onConfirm = {
                pickingRange = false
                model.setDates(it)
            },
        )
    }
}

fun accountTypeLabel(type: AccountType) = when (type) {
    AccountType.Chequing -> "Chequing"
    AccountType.Savings -> "Savings"
    AccountType.CreditCard -> "Credit card"
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T?) -> String,
    onPick: (T) -> Unit,
    dot: (T?) -> androidx.compose.ui.graphics.Color? = { null },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = dot(selected)?.let { color -> { ColorDot(color) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    leadingIcon = dot(option)?.let { color -> { ColorDot(color) } },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/** Picks a range on a calendar; both ends are needed, in either order the person taps them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(initial: DateRange, onDismiss: () -> Unit, onConfirm: (DateRange) -> Unit) {
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.day() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start?.millis(),
        initialSelectedEndDateMillis = initial.end?.millis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedStartDateMillis != null,
                onClick = {
                    val start = picker.selectedStartDateMillis?.day()
                    // One day tapped: that day alone.
                    val end = picker.selectedEndDateMillis?.day() ?: start
                    onConfirm(DateRange(start, end))
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = picker, modifier = Modifier.weight(1f), title = { Text("Custom range", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
