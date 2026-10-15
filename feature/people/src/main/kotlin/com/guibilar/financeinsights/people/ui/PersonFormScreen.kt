package com.guibilar.financeinsights.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.people.PersonDraft
import com.guibilar.financeinsights.core.people.REMINDER_CHOICES
import com.guibilar.financeinsights.core.people.reminderText
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** The person form: "Add person", or "Edit person" with the details of one. Save opens the person (or goes back). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonFormScreen(model: PersonFormViewModel, contentPadding: PaddingValues, onClose: () -> Unit, saved: (String) -> Unit) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (model.isNew) "Add person" else "Edit person", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } },
                actions = {
                    TextButton(onClick = { scope.launch { model.save()?.let(saved) } }, enabled = state.ready && !state.busy) {
                        Text(if (state.busy) "Saving…" else "Save")
                    }
                },
            )
            ReadableColumn {
                when {
                    state.loadError != null -> ErrorState(state.loadError!!, onClose)
                    !state.ready -> Skeleton(Modifier.padding(16.dp), lines = 6, lineHeight = 40.dp)
                    else -> PersonFields(state.draft, state.errors, model::edit)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PersonFields(draft: PersonDraft, errors: Map<String, String>, edit: ((PersonDraft) -> PersonDraft) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Field("Name", draft.name, errors["name"]) { v -> edit { it.copy(name = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Nickname", draft.nickname, null, Modifier.weight(1f)) { v -> edit { it.copy(nickname = v) } }
            Field("Relationship", draft.relationship, null, Modifier.weight(1f)) { v -> edit { it.copy(relationship = v) } }
        }
        Field("Groups", draft.groups, errors["groups"], supporting = "Separate groups with commas, for example Family, Gaming group") { v ->
            edit { it.copy(groups = v) }
        }
        Text("Birthday", style = MaterialTheme.typography.titleSmall)
        DropdownPicker(
            label = "Month",
            options = listOf<Int?>(null) + (1..12).toList(),
            selected = draft.birthMonth,
            text = { month -> month?.let { Month.of(it).getDisplayName(TextStyle.FULL, Locale.US) } ?: "No birthday" },
            onPick = { month -> edit { it.copy(birthMonth = month) } },
            isError = errors["birthday"] != null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Day", draft.birthDay, null, Modifier.weight(1f), number = true) { v -> edit { it.copy(birthDay = v) } }
            Field("Year (optional)", draft.birthYear, null, Modifier.weight(1f), number = true) { v -> edit { it.copy(birthYear = v) } }
        }
        errors["birthday"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Field("Email", draft.email, errors["email"], keyboard = KeyboardType.Email) { v -> edit { it.copy(email = v) } }
        Field("Phone", draft.phone, null, keyboard = KeyboardType.Phone) { v -> edit { it.copy(phone = v) } }
        DropdownPicker(
            label = "Reach-out reminder",
            options = listOf<Int?>(null) + REMINDER_CHOICES.map { it.first } +
                listOfNotNull(draft.contactEveryDays?.takeIf { days -> REMINDER_CHOICES.none { it.first == days } }),
            selected = draft.contactEveryDays,
            text = { days -> if (days == null) "No reminder" else reminderText(days) },
            onPick = { days -> edit { it.copy(contactEveryDays = days) } },
            supportingText = "“Needs attention” shows the person when it is time.",
        )
        OutlinedTextField(
            draft.notes,
            { v -> edit { it.copy(notes = v) } },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    number: Boolean = false,
    keyboard: KeyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
    change: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
    )
}
