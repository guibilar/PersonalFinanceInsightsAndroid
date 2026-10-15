package com.guibilar.financeinsights.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.people.CONTACT_KINDS
import com.guibilar.financeinsights.core.people.HistoryEntry
import com.guibilar.financeinsights.core.people.Interaction
import com.guibilar.financeinsights.core.people.LoanDirection
import com.guibilar.financeinsights.core.people.Person
import com.guibilar.financeinsights.core.people.PersonLink
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.people.birthdayText
import com.guibilar.financeinsights.core.people.groupsLine
import com.guibilar.financeinsights.core.people.historyOf
import com.guibilar.financeinsights.core.people.reminderText
import com.guibilar.financeinsights.ui.common.ChoiceRow
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One person (design canvas, "Person"): the name and groups, "Log contact", "Gift idea", and "Lend item", the strip
 * (birthday, last contact, reminder), the gift ideas (a check marks one given today), the open loans ("Got it back"),
 * the history (contacts and loans), and the links. [canWrite] false (a read-only user) hides every write action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    model: PersonViewModel,
    contentPadding: PaddingValues,
    openLink: (kind: String, targetId: String) -> Unit,
    edit: () -> Unit,
    deleted: () -> Unit,
    onBack: () -> Unit,
    canWrite: Boolean = true,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var action by rememberSaveable { mutableStateOf<PersonAction?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deletingContact by remember { mutableStateOf<Interaction?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText((state.person as? LoadState.Loaded)?.value?.person?.name ?: "Person", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite && state.person is LoadState.Loaded) {
                        IconButton(onClick = edit) { Icon(Icons.Outlined.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    when (val loaded = state.person) {
                        LoadState.Loading -> Skeleton(Modifier.padding(16.dp), lines = 6, lineHeight = 32.dp)
                        is LoadState.Failed -> ErrorState(loaded.message, model::refresh)
                        is LoadState.Loaded -> LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            person(loaded.value, canWrite, state.busy, model, openLink, { action = it }, { deletingContact = it })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val person = (state.person as? LoadState.Loaded)?.value
    when (action) {
        PersonAction.Contact -> ContactDialog(
            busy = state.busy,
            save = { kind, note, day -> scope.launch { if (model.logContact(kind, note, day)) action = null } },
            onDismiss = { action = null },
        )
        PersonAction.Gift -> GiftDialog(
            busy = state.busy,
            save = { title, price, occasion -> scope.launch { if (model.addGift(title, price, occasion)) action = null } },
            onDismiss = { action = null },
        )
        PersonAction.Loan -> LoanDialog(
            name = person?.person?.name.orEmpty(),
            busy = state.busy,
            save = { item, lent, due -> scope.launch { if (model.addLoan(item, lent, due)) action = null } },
            onDismiss = { action = null },
        )
        null -> {}
    }
    if (confirmDelete && person != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${person.person.name}”?") },
            text = { Text("The contacts, gift ideas, loans, and links go too. This cannot be undone.") },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = { scope.launch { if (model.delete()) deleted() else confirmDelete = false } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    deletingContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { deletingContact = null },
            title = { Text("Delete the contact?") },
            text = { Text("The contact of ${formatDay(contact.occurredOn)} goes. The last contact can move back.") },
            confirmButton = {
                TextButton(onClick = { model.deleteContact(contact); deletingContact = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingContact = null }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.person(
    person: Person,
    canWrite: Boolean,
    busy: Boolean,
    model: PersonViewModel,
    openLink: (String, String) -> Unit,
    openAction: (PersonAction) -> Unit,
    deleteContact: (Interaction) -> Unit,
) {
    item(key = "header") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonAvatar(person.person.name, 64.dp)
            Column {
                Text(person.person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                groupsLine(person.person).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (canWrite) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openAction(PersonAction.Contact) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Log contact")
                }
                OutlinedButton(onClick = { openAction(PersonAction.Gift) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Gift idea")
                }
                OutlinedButton(onClick = { openAction(PersonAction.Loan) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Lend item")
                }
            }
        }
    }
    item(key = "strip") { Strip(person) }
    contactDetails(person)

    item(key = "gifts-title") { SectionTitle("Gift ideas") }
    item(key = "gifts") {
        if (person.giftIdeas.isEmpty()) {
            Text("No gift ideas yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            RowsCard {
                person.giftIdeas.forEachIndexed { index, gift ->
                    CardRow(null, last = index == person.giftIdeas.lastIndex) {
                        Checkbox(checked = gift.isGiven, onCheckedChange = { model.toggleGiven(gift) }, enabled = canWrite && !busy)
                        val details = if (gift.isGiven) {
                            listOfNotNull("Given ${formatDay(gift.givenOn!!)}", gift.price?.let(Money::format)).joinToString(" · ")
                        } else {
                            listOfNotNull(gift.occasion, gift.price?.let(Money::format)).joinToString(" · ")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                gift.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (gift.isGiven) TextDecoration.LineThrough else null,
                                color = if (gift.isGiven) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (canWrite && !gift.isGiven) {
                            IconButton(onClick = { model.deleteGift(gift) }, enabled = !busy) { Icon(Icons.Outlined.Delete, "Delete ${gift.title}") }
                        }
                    }
                }
            }
        }
    }

    val open = person.loans.filter { it.isOpen }
    if (open.isNotEmpty()) {
        item(key = "loans-title") { SectionTitle("Lent and borrowed") }
        item(key = "loans") {
            RowsCard {
                open.forEachIndexed { index, loan ->
                    CardRow(null, last = index == open.lastIndex) {
                        val verb = if (loan.direction == LoanDirection.Lent) "Lent" else "Borrowed"
                        TwoLines(
                            loan.itemName,
                            listOfNotNull("$verb ${formatDay(loan.lentOn)}", loan.dueOn?.let { "due ${formatDay(it)}" }, if (loan.overdue) "Not returned" else null)
                                .joinToString(" · "),
                            if (loan.overdue) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                        if (canWrite) {
                            OutlinedButton(onClick = { model.returned(loan) }, enabled = !busy) {
                                Text(if (loan.direction == LoanDirection.Lent) "Got it back" else "Gave it back")
                            }
                        }
                    }
                }
            }
        }
    }

    item(key = "history-title") { SectionTitle("History") }
    val history = historyOf(person)
    item(key = "history") {
        if (history.isEmpty()) {
            Text("No contacts logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            RowsCard {
                history.forEachIndexed { index, entry ->
                    CardRow(null, last = index == history.lastIndex) {
                        when (entry) {
                            is HistoryEntry.Contact -> {
                                TwoLines(
                                    "${formatDay(entry.day)} · ${entry.interaction.kind ?: "Contact"}",
                                    entry.interaction.note,
                                )
                                if (canWrite) {
                                    IconButton(onClick = { deleteContact(entry.interaction) }, enabled = !busy) {
                                        Icon(Icons.Outlined.Delete, "Delete the contact")
                                    }
                                }
                            }
                            is HistoryEntry.Lending -> {
                                val verb = if (entry.loan.direction == LoanDirection.Lent) "Lent" else "Borrowed"
                                TwoLines(
                                    "${formatDay(entry.day)} · $verb",
                                    entry.loan.itemName + (entry.loan.returnedOn?.let { " · back ${formatDay(it)}" } ?: " · not returned"),
                                )
                                if (canWrite) {
                                    IconButton(onClick = { model.deleteLoan(entry.loan) }, enabled = !busy) {
                                        Icon(Icons.Outlined.Delete, "Delete ${entry.loan.itemName}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (person.interactionCount > person.interactions.size) {
        item(key = "history-more") {
            Text(
                "The latest ${person.interactions.size} of ${person.interactionCount} contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (person.links.isNotEmpty()) {
        item(key = "links-title") { SectionTitle("Links") }
        item(key = "links") {
            RowsCard {
                person.links.forEachIndexed { index, link ->
                    CardRow({ openLink(link.kind, link.targetId) }, last = index == person.links.lastIndex) {
                        Icon(Icons.Outlined.Link, null)
                        TwoLines(linkLabel(link), null)
                        if (canWrite) TextButton(onClick = { model.unlink(link) }, enabled = !busy) { Text("Unlink") }
                    }
                }
            }
        }
    }
}

private fun linkLabel(link: PersonLink): String = when (link.kind) {
    PersonLinkKinds.DOCUMENT -> "Document"
    PersonLinkKinds.TASK -> "Task"
    PersonLinkKinds.HOME_ITEM -> "Home item"
    PersonLinkKinds.TRANSACTION -> "Transaction"
    else -> link.kind
}

/** The email, the phone, and the notes, when the person has them. */
private fun LazyListScope.contactDetails(person: Person) {
    val lines = listOfNotNull(person.person.email, person.person.phone, person.notes)
    if (lines.isEmpty()) return
    item(key = "details") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Birthday, last contact, and reminder, side by side. */
@Composable
private fun Strip(person: Person) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            StripCell("Birthday", birthdayText(person.person.birthday).ifEmpty { "—" }, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StripCell(
                "Last contact",
                person.person.lastContactOn?.let { formatDay(it, year = false) } ?: "—",
                Modifier.weight(1f),
                warn = person.person.contactOverdue,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StripCell("Reminder", reminderText(person.person.contactEveryDays), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StripCell(label: String, value: String, modifier: Modifier, warn: Boolean = false) {
    Column(modifier.padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A quick action in a bottom sheet (as the Home module's forms): a title, the fields, Cancel and [confirm]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(title: String, confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onConfirm, enabled = !busy) { Text(confirm) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ContactDialog(busy: Boolean, save: (String?, String, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(CONTACT_KINDS.first()) }
    var note by rememberSaveable { mutableStateOf("") }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    ActionSheet("Log contact", "Log contact", busy, { save(kind, note, day) }, onDismiss) {
        DropdownPicker("Kind", CONTACT_KINDS, kind, { it.orEmpty() }, { kind = it }, Modifier.fillMaxWidth())
        DateField("Day (empty: today)", day, { day = it }, Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
private fun GiftDialog(busy: Boolean, save: (String, Double?, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var occasion by rememberSaveable { mutableStateOf("") }
    ActionSheet("Gift idea", "Add idea", busy, { save(title, price.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 }, occasion) }, onDismiss) {
        OutlinedTextField(title, { title = it }, label = { Text("Gift idea") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            price,
            { price = it },
            label = { Text("Price") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(occasion, { occasion = it }, label = { Text("Occasion") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LoanDialog(name: String, busy: Boolean, save: (String, Boolean, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var item by rememberSaveable { mutableStateOf("") }
    var lent by rememberSaveable { mutableStateOf(true) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    ActionSheet("Lend item", "Add", busy, { save(item, lent, due) }, onDismiss) {
        OutlinedTextField(item, { item = it }, label = { Text("Thing") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoiceRow(listOf(true, false), lent, label = { if (it) "I lent it to $name" else "I borrowed it" }, onSelect = { lent = it })
        DateField("Due back (optional)", due, { due = it }, Modifier.fillMaxWidth())
    }
}
