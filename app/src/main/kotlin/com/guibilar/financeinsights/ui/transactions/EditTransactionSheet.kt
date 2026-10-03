package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.description
import com.guibilar.financeinsights.core.logic.flowsFor
import com.guibilar.financeinsights.core.logic.suggestRuleKeyword
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRulePreview
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.theme.LocalChartColors
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PREVIEW_DEBOUNCE_MS = 300L

/**
 * One transaction: what the bank says (date, amount, account) and what can be corrected (description, category,
 * what it counts as), linking it to a budget, and deleting it. A new category can spread to similar transactions
 * with a rule, as on the web.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTransactionSheet(
    transaction: Transaction,
    categories: List<CategoryReference>,
    budgets: List<Budget>,
    model: TransactionsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = LocalChartColors.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var description by rememberSaveable { mutableStateOf(transaction.description) }
    var categoryId by rememberSaveable { mutableStateOf(transaction.category.id) }
    var flow by rememberSaveable { mutableStateOf(transaction.flow) }
    var alsoSimilar by rememberSaveable { mutableStateOf(false) }
    var keyword by rememberSaveable { mutableStateOf(suggestRuleKeyword(transaction.description)) }
    var preview by remember { mutableStateOf<CategoryRulePreview?>(null) }
    var previewFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // The transaction's own category stays pickable while the list loads.
    val options = if (categories.any { it.id == transaction.category.id }) categories else listOf(transaction.category) + categories
    val flows = flowsFor(transaction.transactionType).let { if (transaction.flow in it) it else listOf(transaction.flow) + it }
    val categoryChanged = categoryId != transaction.category.id
    val descriptionError = when {
        description.isBlank() -> "Enter a description."
        description.length > TextLimits.TRANSACTION_DESCRIPTION -> "At most ${TextLimits.TRANSACTION_DESCRIPTION} characters."
        else -> null
    }
    val keywordError = if (alsoSimilar && categoryChanged && keyword.isBlank()) "Enter a keyword." else null
    val rule = if (alsoSimilar && categoryChanged && keyword.isNotBlank()) CategoryRuleInput(keyword.trim().uppercase(), categoryId, 0) else null

    // What the rule would change, asked once typing pauses.
    LaunchedEffect(rule?.keyword, rule?.categoryId) {
        preview = null
        previewFailed = false
        if (rule == null) return@LaunchedEffect
        delay(PREVIEW_DEBOUNCE_MS)
        preview = model.previewRule(rule.keyword, rule.categoryId, transaction.id)
        // Unanswered, it said "Checking…" for good.
        previewFailed = preview == null
    }

    fun close() = scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    signedAmount(transaction),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.transactionType == TransactionType.Credit) colors.positive else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    listOfNotNull(
                        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US).format(transaction.day),
                        transaction.accountName.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                isError = descriptionError != null,
                supportingText = descriptionError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownPicker(
                label = "Category",
                options = options,
                selected = options.firstOrNull { it.id == categoryId },
                text = { it?.name ?: "" },
                dot = { it?.let { c -> parseHexColor(c.color) } },
                onPick = {
                    categoryId = it.id
                    if (it.id == transaction.category.id) alsoSimilar = false
                },
                supportingText = if (transaction.categoryLocked && !categoryChanged) "Picked by hand: the rules leave it alone." else null,
            )
            if (categoryChanged) {
                Row(
                    Modifier.toggleable(value = alsoSimilar, role = Role.Switch, onValueChange = { alsoSimilar = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Also categorise similar transactions", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Adds a rule, checked first, and applies the rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = alsoSimilar, onCheckedChange = null)
                }
                if (alsoSimilar) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it.take(TextLimits.CATEGORY_RULE_KEYWORD) },
                        label = { Text("Descriptions containing") },
                        isError = keywordError != null,
                        supportingText = {
                            Text(
                                keywordError ?: preview?.let { p ->
                                    if (p.count == 0) "No other transaction would change."
                                    else "${p.count} other ${if (p.count == 1) "transaction" else "transactions"} would change, like ${p.examples.take(2).joinToString(", ")}"
                                } ?: if (previewFailed) "Couldn't check what it would change." else "Checking what it would change…",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text("Counts as", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                flows.forEach { option -> FilterChip(flow == option, onClick = { flow = option }, label = { Text(option.label) }) }
            }
            Text(flow.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Button(
                enabled = !saving && descriptionError == null && keywordError == null,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        val update = TransactionUpdate(description.trim(), categoryId, flow.takeIf { it != transaction.flow })
                        error = model.saveEdit(transaction, update, rule)
                        saving = false
                        if (error == null) close()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save")
            }

            HorizontalDivider()
            BudgetLink(transaction, budgets, model, onDone = ::close)
            HorizontalDivider()
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Delete transaction", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = {
                Text(
                    "“${transaction.description}” leaves the lists, reports and budgets. It is kept hidden, so importing " +
                        "its statement again won't add it back, and it can be restored.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.delete(transaction)
                    close()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Which budget it counts towards: unlink it, or link it to an active one. */
@Composable
private fun BudgetLink(transaction: Transaction, budgets: List<Budget>, model: TransactionsViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Budget?>(null) }
    Text("Budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    val linked = transaction.budget
    if (linked != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Counts towards “${linked.name}”", Modifier.weight(1f))
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    error = model.unlink(transaction)
                    busy = false
                    if (error == null) onDone()
                }
            }) {
                Icon(Icons.Outlined.LinkOff, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Unlink")
            }
        }
    } else if (budgets.isEmpty()) {
        Text("No active budgets to link it to.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownPicker(
                label = "Link to",
                options = budgets,
                selected = picked,
                text = { it?.name ?: "Choose a budget" },
                onPick = { picked = it },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(enabled = picked != null && !busy, onClick = {
                val budget = picked ?: return@OutlinedButton
                busy = true
                scope.launch {
                    error = model.link(transaction, budget)
                    busy = false
                    if (error == null) onDone()
                }
            }) { Text("Link") }
        }
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/** A deleted transaction: what it was, and restoring it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedTransactionSheet(transaction: Transaction, model: TransactionsViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(transaction.description, style = MaterialTheme.typography.titleMedium)
            Text(
                "${signedAmount(transaction)} · ${DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(transaction.day)} · ${transaction.accountName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Deleted: left out of lists, reports and budgets, and kept so its statement doesn't bring it back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {
                model.restore(transaction)
                // Slides away like the other sheets, instead of vanishing.
                scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Restore") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
