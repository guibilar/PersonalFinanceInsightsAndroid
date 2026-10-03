package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.accountTypeLabel
import com.guibilar.financeinsights.core.logic.formatLabel
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch

private fun AccountType.icon() = when (this) {
    AccountType.Chequing -> Icons.Outlined.AccountBalance
    AccountType.Savings -> Icons.Outlined.Savings
    AccountType.CreditCard -> Icons.Outlined.CreditCard
}

@Composable
fun AccountsScreen(model: AccountsViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    SettingsScaffold("Accounts", onBack, contentPadding, model.messages, addLabel = "New account", onAdd = { adding = true }) {
        Text(
            "The bank accounts statements are imported into. Only an account without transactions can be deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        when (val current = state) {
            LoadState.Loading -> Skeleton(lines = 5, lineHeight = 28.dp, modifier = Modifier.padding(16.dp))
            is LoadState.Failed -> ErrorState(current.message, model::reload)
            is LoadState.Loaded -> if (current.value.isEmpty()) EmptyState("No accounts yet. Add one to import statements into.") else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(current.value, key = { it.id }) { account ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = account }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(account.type.icon(), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.name, fontWeight = FontWeight.Medium)
                                // The count shares the second line: as a column of its own it squeezed the name ("Chequin / g").
                                Text(
                                    "${accountTypeLabel(account.type)} · ${formatLabel(account.format)} · " +
                                        "${account.transactionCount} ${if (account.transactionCount == 1) "transaction" else "transactions"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val account = editing
        AccountSheet(
            account = account,
            onSave = { name, type, format ->
                if (account == null) model.add(AccountInput(name, type, format)) else model.update(account, AccountUpdate(name, format))
            },
            onDelete = { deleting = it },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    deleting?.let { account ->
        ConfirmDelete(
            "Delete “${account.name}”?",
            "It has no transactions, so nothing else changes.",
            onConfirm = { model.delete(account) },
            onDismiss = { deleting = null },
        )
    }
}

/** An account: its name, its type (chosen once), its statement format, and deleting it while it is empty. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountSheet(
    account: Account?,
    onSave: suspend (String, AccountType, StatementFormat) -> String?,
    onDelete: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(account?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(account?.type ?: AccountType.Chequing) }
    var format by rememberSaveable { mutableStateOf(account?.format ?: StatementFormat.Cibc) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val nameError = when {
        name.isBlank() -> "Enter a name."
        name.length > TextLimits.ACCOUNT_NAME -> "At most ${TextLimits.ACCOUNT_NAME} characters."
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (account == null) "New account" else "Edit account", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                isError = tried && nameError != null, supportingText = nameError?.takeIf { tried }?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { option ->
                    FilterChip(type == option, onClick = { type = option }, enabled = account == null, label = { Text(accountTypeLabel(option)) })
                }
            }
            Text(
                if (account == null) "Chosen once: it decides what its transactions count as by default." else "The type can't change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Statement files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatementFormat.entries.forEach { option ->
                    FilterChip(format == option, onClick = { format = option }, label = { Text(formatLabel(option)) })
                }
            }
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (nameError != null) return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        failure = onSave(name.trim(), type, format)
                        saving = false
                        if (failure == null) {
                            sheet.hide()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save") }
            if (account != null) {
                if (account.transactionCount > 0) {
                    Text(
                        "It has ${account.transactionCount} transactions (deleted ones included), so it can't be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = {
                        onDelete(account)
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete account", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
