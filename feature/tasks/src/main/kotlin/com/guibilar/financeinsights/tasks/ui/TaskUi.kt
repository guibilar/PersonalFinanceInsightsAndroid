package com.guibilar.financeinsights.tasks.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskDraft
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// What the Tasks screens share: the module labels of the links, the date picker, and the saver of the form.

/** The modules that a task links to, as short labels: "Documents", "Money", "Home". */
fun linkLabels(task: TaskSummary): List<String> = task.links.mapNotNull {
    when (it.kind) {
        TaskLinkKinds.DOCUMENT -> "Documents"
        TaskLinkKinds.TRANSACTION -> "Money"
        TaskLinkKinds.HOME_ITEM -> "Home"
        else -> null
    }
}.distinct()

private fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** A calendar to pick a day; [onPick] gets the day, [onDismiss] closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDayDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.millis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.day()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

/** Keeps what was typed in the task sheet when the phone rotates. */
val TaskDraftSaver: Saver<TaskDraft, Any> = listSaver(
    save = {
        listOf(it.title, it.notes, it.dueOn?.toString(), it.tags, it.repeats, it.every, it.unit.name, it.mode.name, it.sortOrder)
    },
    restore = {
        TaskDraft(
            title = it[0] as String,
            notes = it[1] as String,
            dueOn = (it[2] as String?)?.let(LocalDate::parse),
            tags = it[3] as String,
            repeats = it[4] as Boolean,
            every = it[5] as String,
            unit = RepeatUnit.valueOf(it[6] as String),
            mode = RepeatMode.valueOf(it[7] as String),
            sortOrder = it[8] as Int?,
        )
    },
)
