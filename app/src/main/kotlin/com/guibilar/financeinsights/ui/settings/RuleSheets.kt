package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.FLOW_RULE_FLOWS
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.description
import com.guibilar.financeinsights.core.logic.ruleScope
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.core.model.FlowRuleInput
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch

private fun keywordError(keyword: String, max: Int) = when {
    keyword.isBlank() -> "Enter a keyword."
    keyword.length > max -> "At most $max characters."
    else -> null
}

/** A category rule: its keyword and category, where a new one goes (first or last), and deleting it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryRuleSheet(
    rule: CategoryRule?,
    categories: List<CategoryReference>,
    onSave: suspend (CategoryRuleInput) -> String?,
    onDelete: (CategoryRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf(rule?.keyword ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(rule?.category?.id) }
    var first by rememberSaveable { mutableStateOf(true) }
    val options = if (rule != null && categories.none { it.id == rule.category.id }) listOf(rule.category) + categories else categories
    val category = options.firstOrNull { it.id == categoryId }
    RuleSheet(
        title = if (rule == null) "New rule" else "Edit rule",
        error = keywordError(keyword, TextLimits.CATEGORY_RULE_KEYWORD) ?: if (category == null) "Choose a category." else null,
        onSave = { onSave(CategoryRuleInput(keyword.trim(), category!!.id, if (rule == null && first) 0 else null)) },
        onDelete = rule?.let { { onDelete(it) } },
        deleteMessage = rule?.let {
            "Transactions containing “${it.keyword}” will no longer be categorised as ${it.category.name} by this rule. " +
                "Transactions already imported keep their category until the rules are applied again."
        },
        onDismiss = onDismiss,
    ) {
        KeywordField(keyword, { keyword = it }, "Description contains", "Case doesn't matter, e.g. no frills")
        DropdownPicker(
            label = "Sets the category",
            options = options,
            selected = category,
            text = { it?.name ?: "Choose a category" },
            dot = { it?.let { c -> parseHexColor(c.color) } },
            onPick = { categoryId = it.id },
        )
        if (rule == null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to "Check it first", false to "Check it last").forEachIndexed { index, (value, label) ->
                    SegmentedButton(first == value, onClick = { first = value }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { FitText(label) }
                }
            }
            Text(
                if (first) "It decides before every other rule." else "Other rules that match decide first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A flow rule: its keyword and what it makes a transaction count as. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FlowRuleSheet(rule: FlowRule?, onSave: suspend (FlowRuleInput) -> String?, onDelete: (FlowRule) -> Unit, onDismiss: () -> Unit) {
    var keyword by rememberSaveable { mutableStateOf(rule?.keyword ?: "") }
    var flow by rememberSaveable { mutableStateOf(rule?.flow ?: Flow.Transfer) }
    RuleSheet(
        title = if (rule == null) "New flow rule" else "Edit flow rule",
        error = keywordError(keyword, TextLimits.FLOW_RULE_KEYWORD),
        onSave = { onSave(FlowRuleInput(keyword.trim(), flow)) },
        onDelete = rule?.let { { onDelete(it) } },
        deleteMessage = rule?.let { "Transactions containing “${it.keyword}” will no longer count as ${it.flow.label.lowercase()} by this rule once the rules are applied again." },
        onDismiss = onDismiss,
    ) {
        KeywordField(keyword, { keyword = it }, "Description contains", "e.g. payment thank you")
        Text("Counts as", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FLOW_RULE_FLOWS.forEach { option -> FilterChip(flow == option, onClick = { flow = option }, label = { Text(option.label) }) }
        }
        Text("${flow.description} ${flow.ruleScope}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeywordField(value: String, onChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Placeholder(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The sheet both kinds of rule use: fields, Save, and Delete for an existing rule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    title: String,
    error: String?,
    onSave: suspend () -> String?,
    onDelete: (() -> Unit)?,
    deleteMessage: String?,
    onDismiss: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            fields()
            val shown = failure ?: error?.takeIf { tried }
            if (shown != null) Text(shown, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (error != null) return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        failure = onSave()
                        saving = false
                        if (failure == null) {
                            // Closed even if the sheet leaves the screen while it slides away.
                            try {
                                sheet.hide()
                            } finally {
                                onDismiss()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save") }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete rule", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDelete("Delete rule?", deleteMessage ?: "", onConfirm = {
            onDelete()
            onDismiss()
        }, onDismiss = { confirmDelete = false })
    }
}
