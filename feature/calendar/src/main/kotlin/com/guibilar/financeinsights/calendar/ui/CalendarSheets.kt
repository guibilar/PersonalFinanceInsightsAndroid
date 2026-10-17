package com.guibilar.financeinsights.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.calendar.CalendarEventRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportItem
import com.guibilar.financeinsights.core.calendar.CalendarImportResult
import com.guibilar.financeinsights.core.calendar.REPEAT_PRESETS
import com.guibilar.financeinsights.core.calendar.calendarDay
import com.guibilar.financeinsights.core.calendar.importChanges
import com.guibilar.financeinsights.core.calendar.importSummary
import com.guibilar.financeinsights.core.calendar.isoMoment
import com.guibilar.financeinsights.core.calendar.joinRepeat
import com.guibilar.financeinsights.core.calendar.moment
import com.guibilar.financeinsights.core.calendar.repeatProblem
import com.guibilar.financeinsights.core.calendar.shortDayText
import com.guibilar.financeinsights.core.calendar.splitRepeat
import com.guibilar.financeinsights.core.calendar.timeText
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.TimeField
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The value of the repeat picker for a rule that the presets do not have (an imported event). */
private const val CUSTOM = "custom"

/**
 * Adds or changes an event (T104): all day or with a time, a place, notes, and a repeat (with an end day). For a
 * repeated event opened from one occurrence, "Skip <day>" skips only that day. A read-only user only reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    target: EventTarget,
    canWrite: Boolean,
    busy: Boolean,
    error: String?,
    onSave: (CalendarEventRequest) -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val event = target.event
    val start = moment(event?.startsAt)?.atZoneSameInstant(zone)
    val end = moment(event?.endsAt)?.atZoneSameInstant(zone)
    val (rule, until) = splitRepeat(event?.repeat, zone)
    val isPreset = REPEAT_PRESETS.any { it.rule == rule }

    var title by rememberSaveable { mutableStateOf(event?.title ?: "") }
    var allDay by rememberSaveable { mutableStateOf(event?.allDay ?: false) }
    var startDay by rememberSaveable { mutableStateOf(calendarDay(event?.startDate) ?: start?.toLocalDate() ?: target.day) }
    var endDay by rememberSaveable { mutableStateOf(calendarDay(event?.endDate) ?: end?.toLocalDate() ?: startDay) }
    var startTime by rememberSaveable { mutableStateOf(start?.toLocalTime()?.withSecond(0) ?: LocalTime.of(9, 0)) }
    var endTime by rememberSaveable { mutableStateOf(end?.toLocalTime()?.withSecond(0) ?: LocalTime.of(10, 0)) }
    var repeat by rememberSaveable { mutableStateOf(if (isPreset) rule else CUSTOM) }
    var repeatUntil by rememberSaveable { mutableStateOf(until) }
    var location by rememberSaveable { mutableStateOf(event?.location ?: "") }
    var notes by rememberSaveable { mutableStateOf(event?.notes ?: "") }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    fun request(): CalendarEventRequest? {
        val name = title.trim()
        if (name.isEmpty()) return null.also { problem = "Give the event a title." }
        // An end before the first day would leave the event without a day.
        if (repeat != CUSTOM) repeatProblem(repeat, repeatUntil, startDay)?.let { why -> return null.also { problem = why } }
        val last = if (endDay < startDay) startDay else endDay
        val rrule = if (repeat == CUSTOM) event?.repeat else joinRepeat(repeat, repeatUntil, allDay, zone)
        return if (allDay) {
            CalendarEventRequest(
                name, true, startDate = startDay.toString(), endDate = last.toString(), timeZone = zone.id,
                location = location.trim().ifEmpty { null }, notes = notes.trim().ifEmpty { null }, repeat = rrule,
            )
        } else {
            val startsAt = isoMoment(startDay, startTime, zone)
            val endsAt = isoMoment(last, endTime, zone)
            if (moment(endsAt)!! < moment(startsAt)!!) return null.also { problem = "The end cannot be before the start." }
            CalendarEventRequest(
                name, false, startsAt = startsAt, endsAt = endsAt, timeZone = zone.id,
                location = location.trim().ifEmpty { null }, notes = notes.trim().ifEmpty { null }, repeat = rrule,
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text(
                if (event == null) "New event" else if (canWrite) "Edit event" else "Event",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("Title") },
                placeholder = { Text("Dentist, dinner with Ana…") },
                singleLine = true,
                readOnly = !canWrite,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All day", modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it }, enabled = canWrite)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(if (allDay) "First day" else "Starts", startDay, {
                    startDay = it
                    if (endDay < it) endDay = it
                }, Modifier.weight(1.2f))
                if (!allDay) TimeField("Time", startTime, { startTime = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(if (allDay) "Last day" else "Ends", endDay, { endDay = it }, Modifier.weight(1.2f))
                if (!allDay) TimeField("Time", endTime, { endTime = it }, Modifier.weight(1f))
            }
            val options = REPEAT_PRESETS.map { it.rule } + if (isPreset) emptyList() else listOf(CUSTOM)
            DropdownPicker(
                label = "Repeat",
                options = options,
                selected = repeat,
                text = { value -> if (value == CUSTOM) "As imported" else REPEAT_PRESETS.firstOrNull { it.rule == value }?.label ?: "" },
                onPick = { repeat = it },
                enabled = canWrite,
                modifier = Modifier.fillMaxWidth(),
            )
            if (repeat.isNotEmpty() && repeat != CUSTOM) {
                DateField("Until (optional)", repeatUntil, { repeatUntil = it }, Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(300) },
                label = { Text("Place") },
                singleLine = true,
                readOnly = !canWrite,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(4000) },
                label = { Text("Notes") },
                minLines = 2,
                readOnly = !canWrite,
                modifier = Modifier.fillMaxWidth(),
            )
            (problem ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (canWrite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event != null) {
                        TextButton(onClick = { confirmingDelete = true }, enabled = !busy) {
                            Text(if (event.repeat != null) "Delete all" else "Delete", color = MaterialTheme.colorScheme.error)
                        }
                        val occurrence = target.occurrence
                        if (event.repeat != null && occurrence != null) {
                            TextButton(onClick = onSkip, enabled = !busy) { Text("Skip ${shortDayText(occurrence)}") }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        problem = null
                        request()?.let(onSave)
                    }, enabled = !busy) { Text(if (busy) "Saving…" else "Save") }
                }
            }
        }
    }

    if (confirmingDelete && event != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete the event?") },
            text = { Text(if (event.repeat != null) "All its days go from the calendar." else "It goes from the calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** What an import of an .ics file would do, with "Import" (T104, T101). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(result: CalendarImportResult, busy: Boolean, onImport: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text("Import events", style = MaterialTheme.typography.titleLarge)
            Text(importSummary(result), style = MaterialTheme.typography.bodyLarge)
            result.events.forEach { ImportRow(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onImport, enabled = !busy && importChanges(result) > 0) { Text(if (busy) "Importing…" else "Import") }
            }
        }
    }
}

@Composable
private fun ImportRow(event: CalendarImportItem) {
    val day = calendarDay(event.startDate)
    val whenText = when {
        event.startsAt != null -> listOfNotNull(
            moment(event.startsAt)?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()?.let(::shortDayText),
            timeText(event.startsAt),
        ).joinToString(" · ")
        day != null -> "${shortDayText(day)} · All day"
        else -> ""
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            if (event.action == "Same") "Already there" else event.action,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = when (event.action) {
                "Add" -> MaterialTheme.colorScheme.primary
                "Skip", "Delete" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(whenText.ifEmpty { null }, event.reason).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The local day of the occurrence of a moment, in the phone's zone. */
@Suppress("unused")
internal fun localDay(text: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? = moment(text)?.atZoneSameInstant(zone)?.toLocalDate()
