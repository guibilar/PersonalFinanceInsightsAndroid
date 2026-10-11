package com.guibilar.financeinsights.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.guibilar.financeinsights.core.logic.formatDay

/**
 * A field that opens a list to pick from. [dot] gives an option a colour dot (categories). A long list (categories,
 * say) can be searched: typing in the open field narrows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownPicker(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T?) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    dot: (T?) -> Color? = { null },
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val searchable = options.size > SEARCH_FROM
    var query by remember { mutableStateOf("") }
    val searching = searchable && expanded
    val shown = if (searching && query.isNotBlank()) options.filter { text(it).contains(query.trim(), ignoreCase = true) } else options
    fun close() {
        expanded = false
        query = ""
        // A search field keeps the keyboard up otherwise, after the choice is made.
        if (searchable) focus.clearFocus()
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) { if (it) expanded = true else close() } },
        modifier = modifier,
    ) {
        OutlinedTextField(
            // Open and searchable, the field takes the search; the choice shows behind it.
            value = if (searching) query else text(selected),
            onValueChange = {
                query = it
                expanded = true
            },
            readOnly = !searchable,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = if (searching) ({ Text(text(selected), maxLines = 1) }) else null,
            leadingIcon = (if (searching) null else dot(selected))?.let { color -> { ColorDot(color) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = supportingText?.let { { Text(it) } },
            isError = isError,
            modifier = Modifier.fillMaxWidth().menuAnchor(
                if (searchable) ExposedDropdownMenuAnchorType.PrimaryEditable else ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = ::close) {
            shown.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    leadingIcon = dot(option)?.let { color -> { ColorDot(color) } },
                    onClick = {
                        close()
                        onPick(option)
                    },
                )
            }
            if (shown.isEmpty()) DropdownMenuItem(text = { Text("Nothing matches “${query.trim()}”") }, onClick = {}, enabled = false)
        }
    }
}

/** From this many options on, a picker can be searched by typing. */
private const val SEARCH_FROM = 8

private fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.day(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** A day, picked on a calendar: no typing, so no half-typed years. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    var picking by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    // A tap anywhere on the field opens the calendar.
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) picking = true }
    }
    OutlinedTextField(
        interactionSource = interaction,
        value = value?.let { formatDay(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        isError = isError,
        trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Outlined.CalendarMonth, "Pick $label") } },
        modifier = modifier,
    )
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: LocalDate.now()).millis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it.day()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
