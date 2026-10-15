package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.guibilar.financeinsights.core.logic.DateChoice
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.logic.dateRange
import com.guibilar.financeinsights.core.logic.describeRange
import com.guibilar.financeinsights.core.logic.matchDatePreset
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.TransactionSortField

@Composable
internal fun MoreMenu(onImport: (() -> Unit)?, onApplyRules: (() -> Unit)?, onShowDeleted: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        // Null for a read-only user (T75): the item is not there.
        if (onImport != null) {
            DropdownMenuItem(
                text = { Text("Import a statement") },
                leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                onClick = { open = false; onImport() },
            )
        }
        if (onApplyRules != null) {
            DropdownMenuItem(
                text = { Text("Apply the rules") },
                leadingIcon = { Icon(Icons.Outlined.Rule, null) },
                onClick = { open = false; onApplyRules() },
            )
        }
        DropdownMenuItem(
            text = { Text("Deleted transactions") },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
            onClick = { open = false; onShowDeleted() },
        )
    }
}

/** A criterion that is set, as a removable chip. [key] is the criterion, as two labels can match ("Savings"). */
internal data class FilterChipModel(val key: String, val label: String, val remove: () -> Unit)

internal fun filterChips(state: TransactionsState, model: TransactionsViewModel): List<FilterChipModel> {
    val criteria = state.criteria
    return buildList {
        criteria.description?.let { add(FilterChipModel("search", "“$it”", model::clearSearch)) }
        criteria.categoryId?.let { id ->
            add(FilterChipModel("category", state.categories.firstOrNull { it.id == id }?.name ?: "Category") { model.setCategory(null) })
        }
        criteria.tagId?.let { id ->
            add(FilterChipModel("tag", "#" + (state.tags.firstOrNull { it.id == id }?.name ?: "Tag")) { model.setTag(null) })
        }
        criteria.accountId?.let { id ->
            add(FilterChipModel("account", state.accounts.firstOrNull { it.id == id }?.name ?: "Account") { model.setAccount(null) })
        }
        criteria.flow?.let { add(FilterChipModel("flow", it.label) { model.setFlow(null) }) }
        when (val choice = matchDatePreset(criteria, state.today)) {
            DateChoice.All -> {}
            is DateChoice.Preset -> add(FilterChipModel("dates", "${choice.preset.label} · ${describeRange(criteria.dateRange())}") { model.setDates(DateRange()) })
            DateChoice.Custom -> add(FilterChipModel("dates", describeRange(criteria.dateRange())) { model.setDates(DateRange()) })
        }
    }
}

@Composable
internal fun SortMenu(field: TransactionSortField, direction: SortDirection, onSort: (TransactionSortField) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        SORT_LABELS.forEach { (option, label) ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontWeight = if (option == field) FontWeight.SemiBold else null)
                    }
                },
                trailingIcon = {
                    if (option == field) {
                        Icon(
                            if (direction == SortDirection.Ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            if (direction == SortDirection.Ascending) "Ascending" else "Descending",
                        )
                    }
                },
                onClick = {
                    open = false
                    onSort(option)
                },
            )
        }
    }
}
