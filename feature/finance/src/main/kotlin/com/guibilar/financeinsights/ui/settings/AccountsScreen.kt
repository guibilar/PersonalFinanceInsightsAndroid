package com.guibilar.financeinsights.ui.settings

import com.guibilar.financeinsights.ui.LocalFinanceCanWrite
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.logic.parseAmount
import com.guibilar.financeinsights.core.logic.accountTypeLabel
import com.guibilar.financeinsights.core.logic.formatLabel
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.AccountBalance
import com.guibilar.financeinsights.core.model.AccountInput
import com.guibilar.financeinsights.core.model.AccountType
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.RegisteredPlan
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberOpenItem
import kotlinx.coroutines.launch

private fun AccountType.icon() = when (this) {
    AccountType.Chequing -> Icons.Outlined.AccountBalance
    AccountType.Savings -> Icons.Outlined.Savings
    AccountType.CreditCard -> Icons.Outlined.CreditCard
    AccountType.Investment -> Icons.AutoMirrored.Outlined.ShowChart
}

/** "$3,500.00 of $7,000.00 room left this year · $3,500.00 in", or what's missing. */
fun roomText(account: Account): String {
    val room = account.contributionRoom
    val left = account.roomLeft
    val moves = buildString {
        append("${Money.format(account.depositedThisYear)} in")
        if (account.withdrawnThisYear > 0) append(" · ${Money.format(account.withdrawnThisYear)} out (room again in January)")
    }
    return when {
        left == null || room == null -> "${Money.format(account.depositedThisYear)} deposited this year. Enter this year's room to see what's left."
        left < 0 -> "Over by ${Money.format(-left)} · $moves"
        else -> "${Money.format(left)} of ${Money.format(room)} room left this year · $moves"
    }
}

/** A balance as typed: an amount, negative with a leading minus (an overdraft). */
fun parseBalance(text: String): Double? {
    val trimmed = text.trim()
    val negative = trimmed.startsWith("-") || trimmed.startsWith("−")
    val amount = parseAmount(if (negative) trimmed.drop(1) else trimmed) ?: return null
    return if (negative) -amount else amount
}

@Composable
fun AccountsScreen(model: AccountsViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val accounts = state.valueOrNull.orEmpty()
    // Saved, so a sheet stays open (with what was typed) when the phone rotates.
    var editing by rememberOpenItem(accounts) { it.id }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberOpenItem(accounts) { it.id }
    var balancesOf by rememberOpenItem(accounts) { it.id }

    val canWrite = LocalFinanceCanWrite.current
    SettingsScaffold("Accounts", onBack, contentPadding, model.messages, addLabel = if (canWrite) "New account" else null, onAdd = { adding = true }) {
        Text(
            "The bank accounts, cards and investments statements are imported into. Tap the balance to enter what one holds; only an account without transactions can be deleted.",
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
                            Modifier.fillMaxWidth().clickable(enabled = canWrite) { editing = account }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(account.type.icon(), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.name, fontWeight = FontWeight.Medium)
                                // The count shares the second line: as a column of its own it squeezed the name ("Chequin / g").
                                Text(
                                    listOfNotNull(
                                        accountTypeLabel(account.type),
                                        account.plan.takeIf { it != RegisteredPlan.None }?.label,
                                        formatLabel(account.format),
                                        "${account.transactionCount} ${if (account.transactionCount == 1) "transaction" else "transactions"}",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (account.plan != RegisteredPlan.None) {
                                    val room = account.contributionRoom
                                    val left = account.roomLeft
                                    if (room != null && left != null && room > 0) {
                                        LinearProgressIndicator(
                                            progress = { ((room - left) / room).toFloat().coerceIn(0f, 1f) },
                                            color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).fillMaxWidth(0.7f),
                                        )
                                    }
                                    Text(
                                        roomText(account),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if ((left ?: 0.0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { balancesOf = account },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.semantics { contentDescription = "Balances of ${account.name}" },
                            ) {
                                val balance = account.balance
                                if (balance == null) Text("Add balance")
                                else Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        (if (account.type == AccountType.CreditCard) "Owes " else "") + Money.format(balance.amount),
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(formatDay(balance.day, year = false), style = MaterialTheme.typography.labelSmall)
                                }
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
            onSave = { input ->
                if (account == null) model.add(input)
                else model.update(account, AccountUpdate(input.name, input.format, input.plan, input.contributionRoom, input.contributionRoomYear))
            },
            onDelete = { deleting = it },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    balancesOf?.let { account -> BalancesSheet(account, model, onDismiss = { balancesOf = null }) }
    deleting?.let { account ->
        ConfirmDelete(
            "Delete “${account.name}”?",
            "It has no transactions, so nothing else changes (its balances go with it).",
            onConfirm = { model.delete(account) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * An account: its name, its type (chosen once), its statement format, for savings and investments its registered plan
 * and the year's contribution room, and deleting it while it is empty.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AccountSheet(
    account: Account?,
    onSave: suspend (AccountInput) -> String?,
    onDelete: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(account?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(account?.type ?: AccountType.Chequing) }
    var format by rememberSaveable { mutableStateOf(account?.format ?: StatementFormat.Cibc) }
    var plan by rememberSaveable { mutableStateOf(account?.plan ?: RegisteredPlan.None) }
    val thisYear = remember { java.time.LocalDate.now().year }
    // Last year's room is no use this year: the field asks for this year's (left empty, the old one stays as it is).
    val roomIsOld = (account?.contributionRoomYear ?: thisYear) < thisYear
    var room by rememberSaveable {
        mutableStateOf(if (roomIsOld) "" else account?.contributionRoom?.let { Money.plain(it) } ?: "")
    }
    var roomYear by rememberSaveable {
        mutableStateOf((if (roomIsOld) thisYear else account?.contributionRoomYear ?: thisYear).toString())
    }
    val hasPlan = type.holdsSavings && plan != RegisteredPlan.None
    val roomError = when {
        !hasPlan || room.isBlank() -> null
        parseAmount(room) == null -> "Enter an amount, like 7000."
        else -> null
    }
    val yearError = roomYear.toIntOrNull().let { if (hasPlan && room.isNotBlank() && (it == null || it < 2009 || it > thisYear)) "From 2009 to $thisYear." else null }
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
            if (type.holdsSavings) {
                Text("Registered plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RegisteredPlan.entries.forEach { option ->
                        FilterChip(plan == option, onClick = { plan = option }, label = { Text(option.label) })
                    }
                }
                if (hasPlan) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = room, onValueChange = { room = it }, label = { Text("Contribution room") },
                            prefix = { Text("$") }, singleLine = true, isError = roomError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = { Text(roomError ?: "For a TFSA, your room on January 1.") },
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = roomYear, onValueChange = { roomYear = it.filter(Char::isDigit).take(4) }, label = { Text("Year") },
                            singleLine = true, isError = yearError != null, supportingText = yearError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (nameError != null || roomError != null || yearError != null) return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        val roomAmount = if (hasPlan) parseAmount(room) else null
                        failure = onSave(
                            AccountInput(
                                name.trim(), type, format,
                                plan = plan.takeIf { type.holdsSavings },
                                contributionRoom = roomAmount,
                                contributionRoomYear = roomAmount?.let { roomYear.toInt() },
                            ),
                        )
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
            if (account != null) {
                if (account.transactionCount > 0) {
                    Text(
                        "It has ${account.transactionCount} ${if (account.transactionCount == 1) "transaction" else "transactions"} (deleted ones included), so it can't be deleted.",
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


/** An account's balances: enter what it holds (a card: owes) at the end of a day, and the ones entered before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalancesSheet(account: Account, model: AccountsViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var balances by remember { mutableStateOf<List<AccountBalance>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(java.time.LocalDate.now()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    suspend fun refresh() {
        val loaded = model.balances(account)
        balances = loaded ?: balances
        loadFailed = loaded == null
    }
    LaunchedEffect(account.id) { refresh() }
    val card = account.type == AccountType.CreditCard

    // T75: a read-only user sees the balances, without the form or the deletes.
    val canWrite = LocalFinanceCanWrite.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Balances · ${account.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    account.type == AccountType.Investment ->
                        "What it's worth, as your broker shows it. Enter it now and then: between balances only money in and out moves it, not the market."
                    card -> "What the card owes at the end of that day, as its statement or app shows it."
                    else -> "What the account holds at the end of that day. Its transactions carry it on from there."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canWrite) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                DateField("On", date, onChange = { date = it }, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(if (card) "Owed" else "Balance") },
                    prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            if (canWrite) Button(
                enabled = !saving && parseBalance(amount) != null,
                onClick = {
                    val value = parseBalance(amount) ?: return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        failure = model.setBalance(account, date, value)
                        saving = false
                        if (failure == null) {
                            amount = ""
                            refresh()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save balance") }
            HorizontalDivider()
            val list = balances
            when {
                list == null && loadFailed -> Text("Couldn't load the balances.", color = MaterialTheme.colorScheme.error)
                list == null -> CircularProgressIndicator(Modifier.size(24.dp))
                list.isEmpty() -> Text("No balances entered yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> list.forEach { balance ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDay(balance.day), Modifier.weight(1f))
                        Text(Money.format(balance.amount), fontWeight = FontWeight.Medium)
                        // Asked first, as on the web: a balance deleted can't be brought back.
                        if (canWrite) {
                            IconButton(onClick = { deletingId = balance.id }) {
                                Icon(Icons.Outlined.Delete, "Delete the balance of ${formatDay(balance.day)}")
                            }
                        }
                    }
                }
            }
            balances?.firstOrNull { it.id == deletingId }?.let { balance ->
                ConfirmDelete(
                    "Delete this balance?",
                    "The balance of ${formatDay(balance.day)}, ${Money.format(balance.amount)}, is deleted.",
                    onConfirm = {
                        deletingId = null
                        scope.launch {
                            failure = model.deleteBalance(account, balance)
                            refresh()
                        }
                    },
                    onDismiss = { deletingId = null },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
