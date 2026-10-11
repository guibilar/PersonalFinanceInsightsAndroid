package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.theme.parseHexColor

/**
 * The top bar while transactions are ticked: how many, ticking all that are loaded, and one change for them all (a
 * category, what they count as, a budget to link to or unlinking, a tag to add or remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBar(state: TransactionsState, model: TransactionsViewModel) {
    var menu by remember { mutableStateOf<String?>(null) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = { IconButton(onClick = model::clearSelection) { Icon(Icons.Outlined.Close, "Stop selecting") } },
        title = { FitText("${state.selected.size} selected", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
        actions = {
            IconButton(onClick = model::selectAll) { Icon(Icons.Outlined.SelectAll, "Select all loaded") }
            Box {
                IconButton(onClick = { menu = "category" }) { Icon(Icons.Outlined.Sell, "Set the category") }
                DropdownMenu(expanded = menu == "category", onDismissRequest = { menu = null }) {
                    state.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            leadingIcon = { ColorDot(parseHexColor(category.color)) },
                            onClick = {
                                menu = null
                                model.bulk(TransactionBulkUpdate(emptyList(), categoryId = category.id))
                            },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = "flow" }) { Icon(Icons.Outlined.SwapHoriz, "Set what they count as") }
                DropdownMenu(expanded = menu == "flow", onDismissRequest = { menu = null }) {
                    Flow.entries.forEach { flow ->
                        DropdownMenuItem(
                            text = { Text(flow.label) },
                            onClick = {
                                menu = null
                                model.bulk(TransactionBulkUpdate(emptyList(), flow = flow))
                            },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = "budget" }) { Icon(Icons.Outlined.Link, "Link to a budget") }
                DropdownMenu(expanded = menu == "budget", onDismissRequest = { menu = null }) {
                    state.budgets.forEach { budget ->
                        DropdownMenuItem(
                            text = { Text("Link to ${budget.name}") },
                            onClick = {
                                menu = null
                                model.bulk(TransactionBulkUpdate(emptyList(), budgetId = budget.id))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Unlink from budgets") },
                        leadingIcon = { Icon(Icons.Outlined.LinkOff, null) },
                        onClick = {
                            menu = null
                            model.bulk(TransactionBulkUpdate(emptyList(), unlinkBudget = true))
                        },
                    )
                }
            }
            if (state.tags.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = "tag" }) { Icon(Icons.Outlined.Tag, "Add or remove a tag") }
                    DropdownMenu(expanded = menu == "tag", onDismissRequest = { menu = null }) {
                        state.tags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text("Add #${tag.name}") },
                                onClick = {
                                    menu = null
                                    model.bulk(TransactionBulkUpdate(emptyList(), addTagId = tag.id))
                                },
                            )
                        }
                        HorizontalDivider()
                        state.tags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text("Remove #${tag.name}") },
                                onClick = {
                                    menu = null
                                    model.bulk(TransactionBulkUpdate(emptyList(), removeTagId = tag.id))
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
