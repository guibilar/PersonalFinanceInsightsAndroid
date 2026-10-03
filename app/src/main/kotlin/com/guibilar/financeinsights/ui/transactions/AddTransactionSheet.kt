package com.guibilar.financeinsights.ui.transactions

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.amountError
import com.guibilar.financeinsights.core.logic.flowsFor
import com.guibilar.financeinsights.core.logic.parseAmount
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Adds a transaction by hand, for an account whose bank has no statement files (or a purchase not on a statement
 * yet). It starts on the day and account of the one added just before, and on a by-hand account otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(state: TransactionsState, model: TransactionsViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previous = state.lastAdded
    var accountId by rememberSaveable {
        mutableStateOf(previous?.second ?: state.accounts.firstOrNull { it.format == StatementFormat.Manual }?.id)
    }
    var date by rememberSaveable { mutableStateOf(previous?.first ?: state.today) }
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(TransactionType.Debit) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var flow by rememberSaveable { mutableStateOf<Flow?>(null) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val descriptionError = when {
        description.isBlank() -> "Enter a description."
        description.length > TextLimits.TRANSACTION_DESCRIPTION -> "At most ${TextLimits.TRANSACTION_DESCRIPTION} characters."
        else -> null
    }
    val amountProblem = amountError(amount)
    val valid = accountId != null && descriptionError == null && amountProblem == null

    fun submit(allowDuplicate: Boolean) {
        tried = true
        if (!valid || saving) return
        saving = true
        error = null
        scope.launch {
            val input = TransactionInput(
                date = date.toString(),
                description = description.trim(),
                amount = parseAmount(amount)!!,
                transactionType = direction,
                accountId = accountId!!,
                categoryId = categoryId,
                flow = flow,
                allowDuplicate = allowDuplicate,
            )
            when (val outcome = model.add(input)) {
                AddOutcome.Added -> {
                    sheet.hide()
                    onDismiss()
                }
                is AddOutcome.Duplicate -> duplicate = outcome.message
                is AddOutcome.Failed -> error = outcome.message
            }
            saving = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a transaction", style = MaterialTheme.typography.titleLarge)
            DropdownPicker<Account>(
                label = "Account",
                options = state.accounts,
                selected = state.accounts.firstOrNull { it.id == accountId },
                text = { it?.let { a -> "${a.name} · ${accountTypeLabel(a.type)}" } ?: "Choose an account" },
                onPick = {
                    accountId = it.id
                    duplicate = null
                },
            )
            DateField("Date", date, onChange = {
                date = it
                duplicate = null
            }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    duplicate = null
                },
                label = { Text("Description") },
                isError = tried && descriptionError != null,
                supportingText = (descriptionError.takeIf { tried })?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    duplicate = null
                },
                label = { Text("Amount") },
                prefix = { Text("$") },
                isError = tried && amountProblem != null,
                supportingText = (amountProblem.takeIf { tried })?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.Debit to "Money out", TransactionType.Credit to "Money in").forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = direction == type,
                        onClick = {
                            direction = type
                            if (flow != null && flow !in flowsFor(type)) flow = null
                            duplicate = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }
            val categories: List<CategoryReference?> = listOf(null) + state.categories.map { it.reference }
            DropdownPicker(
                label = "Category",
                options = categories,
                selected = categories.firstOrNull { it?.id == categoryId },
                text = { it?.name ?: "Let the rules decide" },
                dot = { it?.let { c -> parseHexColor(c.color) } },
                onPick = { categoryId = it?.id },
            )
            Text("Counts as", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(flow == null, onClick = { flow = null }, label = { Text("Let the rules decide") })
                flowsFor(direction).forEach { option ->
                    FilterChip(flow == option, onClick = { flow = option }, label = { Text(option.label) })
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (duplicate != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(duplicate!!, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Two identical purchases on the same day are fine: add it anyway.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        OutlinedButton(onClick = { submit(allowDuplicate = true) }, enabled = !saving) { Text("Add anyway") }
                    }
                }
            }
            Button(
                onClick = { submit(allowDuplicate = false) },
                enabled = !saving && duplicate == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Add")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
