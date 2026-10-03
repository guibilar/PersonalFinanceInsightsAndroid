package com.guibilar.financeinsights.ui.budgets

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.amountError
import com.guibilar.financeinsights.core.logic.hint
import com.guibilar.financeinsights.core.logic.parseAmount
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Why the budget can't be saved as it is, by field; empty when it can. */
internal fun budgetErrors(
    name: String,
    description: String,
    amount: String,
    period: BudgetPeriod,
    start: LocalDate?,
    end: LocalDate?,
    merchant: String,
): Map<String, String> = buildMap {
    when {
        name.isBlank() -> put("name", "Enter a name.")
        name.length > TextLimits.BUDGET_NAME -> put("name", "At most ${TextLimits.BUDGET_NAME} characters.")
    }
    when {
        description.isBlank() -> put("description", "Enter a description.")
        description.length > TextLimits.BUDGET_DESCRIPTION -> put("description", "At most ${TextLimits.BUDGET_DESCRIPTION} characters.")
    }
    amountError(amount)?.let { put("amount", it) }
    if (period == BudgetPeriod.Custom) {
        when {
            start == null || end == null -> put("days", "Choose the first and the last day.")
            start > end -> put("days", "The last day can't be before the first.")
        }
    }
    if (merchant.length > TextLimits.BUDGET_MERCHANT) put("merchant", "At most ${TextLimits.BUDGET_MERCHANT} characters.")
}

/**
 * A new budget, or [budget] to edit. A budget with a period can count the period's spending by itself: in some
 * categories, or at a merchant (one or the other); a total budget counts only what is linked to it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetFormSheet(
    budget: Budget?,
    categories: List<CategoryReference>,
    onSave: suspend (BudgetInput) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(budget?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(budget?.description ?: "") }
    var amount by rememberSaveable { mutableStateOf(budget?.totalAmount?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    var active by rememberSaveable { mutableStateOf(budget?.status != Status.Inactive) }
    var period by rememberSaveable { mutableStateOf(budget?.period ?: BudgetPeriod.Total) }
    var start by rememberSaveable { mutableStateOf(budget?.startDate?.take(10)?.let(LocalDate::parse)) }
    var end by rememberSaveable { mutableStateOf(budget?.endDate?.take(10)?.let(LocalDate::parse)) }
    var categoryIds by rememberSaveable { mutableStateOf(budget?.categories.orEmpty().map { it.id }.toSet()) }
    var merchant by rememberSaveable { mutableStateOf(budget?.merchant ?: "") }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // An edited budget's own categories stay pickable while the list loads.
    val options = (budget?.categories.orEmpty().filter { own -> categories.none { it.id == own.id } }) + categories
    val errors = budgetErrors(name, description, amount, period, start, end, merchant)
    fun shown(field: String) = errors[field]?.takeIf { tried }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (budget == null) "New budget" else "Edit budget", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                isError = shown("name") != null, supportingText = shown("name")?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it }, label = { Text("Description") },
                isError = shown("description") != null, supportingText = shown("description")?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, prefix = { Text("$") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = shown("amount") != null, supportingText = shown("amount")?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Period", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BudgetPeriod.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = period == option,
                        onClick = {
                            period = option
                            // A total budget has no period's spending to count by itself.
                            if (option == BudgetPeriod.Total) {
                                categoryIds = emptySet()
                                merchant = ""
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, BudgetPeriod.entries.size),
                    ) { Text(option.name) }
                }
            }
            Text(period.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (period == BudgetPeriod.Custom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("From", start, { start = it }, Modifier.weight(1f), isError = shown("days") != null)
                    DateField("To", end, { end = it }, Modifier.weight(1f), isError = shown("days") != null)
                }
                shown("days")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            if (period != BudgetPeriod.Total) {
                Text("Counts by itself", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "The period's spending in these categories, or at a merchant, counts without linking. Leave both empty to count only what you link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val categoriesLocked = merchant.isNotBlank()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { category ->
                        val chosen = category.id in categoryIds
                        FilterChip(
                            selected = chosen,
                            enabled = !categoriesLocked || chosen,
                            onClick = { categoryIds = if (chosen) categoryIds - category.id else categoryIds + category.id },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(parseHexColor(category.color)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Or a merchant") },
                    placeholder = { Text("e.g. no frills") },
                    enabled = categoryIds.isEmpty(),
                    singleLine = true,
                    isError = shown("merchant") != null,
                    supportingText = {
                        Text(shown("merchant") ?: if (categoryIds.isEmpty()) "Spending whose description contains this, ignoring case." else "A budget counts categories or a merchant, not both.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                Modifier.toggleable(value = active, role = Role.Switch, onValueChange = { active = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "An inactive budget keeps its transactions but takes no new ones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = active, onCheckedChange = null)
            }

            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (errors.isNotEmpty()) return@Button
                    saving = true
                    error = null
                    scope.launch {
                        val input = BudgetInput(
                            name = name.trim(),
                            description = description.trim(),
                            status = if (active) Status.Active else Status.Inactive,
                            amount = parseAmount(amount)!!,
                            period = period,
                            startDate = if (period == BudgetPeriod.Custom) start?.toString() else null,
                            endDate = if (period == BudgetPeriod.Custom) end?.toString() else null,
                            categoryIds = if (period == BudgetPeriod.Total) emptyList() else categoryIds.toList(),
                            merchant = merchant.trim().takeIf { period != BudgetPeriod.Total && it.isNotEmpty() },
                        )
                        error = onSave(input)
                        saving = false
                        if (error == null) {
                            sheet.hide()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(if (budget == null) "Add budget" else "Save")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
