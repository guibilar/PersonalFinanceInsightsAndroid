package com.guibilar.financeinsights.calendar.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.calendar.CalendarItem
import com.guibilar.financeinsights.core.calendar.accentId
import com.guibilar.financeinsights.core.calendar.calendarDay
import com.guibilar.financeinsights.core.calendar.dayHeading
import com.guibilar.financeinsights.core.calendar.itemsOn
import com.guibilar.financeinsights.core.calendar.moduleLabel
import com.guibilar.financeinsights.core.calendar.monthText
import com.guibilar.financeinsights.core.calendar.monthWeeks
import com.guibilar.financeinsights.core.calendar.whenText
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.accentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The biggest .ics file that the screen sends (the server takes 5 million characters). */
const val MAX_ICS_BYTES = 5_000_000

/** The text of a picked or shared file; null when it cannot be read or is too big. */
fun readText(context: Context, uri: Uri, maxBytes: Int = MAX_ICS_BYTES): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        // At most one byte more than the limit (readNBytes needs Android 13).
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= maxBytes) {
            val read = stream.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        if (out.size() > maxBytes) null else out.toString(Charsets.UTF_8.name())
    }
}.getOrNull()

/** The icon of an item, by its kind. */
fun iconOf(item: CalendarItem): ImageVector = when (item.kind) {
    "task-due" -> Icons.Outlined.TaskAlt
    "birthday" -> Icons.Outlined.Cake
    "reach-out" -> Icons.Outlined.Person
    "loan-due" -> Icons.Outlined.Handshake
    "document-expires" -> Icons.Outlined.Description
    "warranty-ends" -> Icons.Outlined.House
    else -> Icons.Outlined.Event
}

/**
 * The Calendar screen (T104): the month (the items of each day as dots, the list of the chosen day below), or the agenda.
 * An event opens its sheet; another module's item opens its page ([onOpenLink], the app's link map). [canWrite] false
 * (a read-only user) shows no add, change, or import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    model: CalendarViewModel,
    contentPadding: PaddingValues,
    canWrite: Boolean = true,
    onOpenLink: (String) -> Unit = {},
    sharedIcs: MutableStateFlow<String?> = remember { MutableStateFlow(null) },
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    // An .ics file shared to the app (T101): its preview, once.
    val shared by sharedIcs.collectAsState()
    LaunchedEffect(shared) {
        shared?.let {
            sharedIcs.value = null
            if (canWrite) model.preview(it) else model.message("You can only read the calendar.")
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                if (text == null) model.message("Could not read that file (at most 5 MB).") else model.preview(text)
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Calendar", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (state.agenda) {
                        IconButton(onClick = { model.showAgenda(false) }) { Icon(Icons.Outlined.CalendarMonth, "Month") }
                    } else {
                        IconButton(onClick = { model.showAgenda(true) }) { Icon(Icons.AutoMirrored.Outlined.ViewList, "Agenda") }
                    }
                    if (canWrite) {
                        IconButton(onClick = { pick.launch("*/*") }, enabled = !state.busy) { Icon(Icons.Outlined.FileUpload, "Import .ics") }
                    }
                },
            )
            ReadableColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.partial.isNotEmpty()) {
                            item(key = "partial") {
                                Text(
                                    "Some dates could not load: ${state.partial.joinToString(", ")}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (state.agenda) agenda(state, model, onOpenLink) else month(state, model, onOpenLink)
                    }
                }
            }
        }
        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = { model.newEvent() },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add event") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.target?.let { target ->
        // Its fields start from the target: another one opened over it (a link's event answering late) starts afresh.
        key(target) {
            EventSheet(
                target = target,
                canWrite = canWrite,
                busy = state.busy,
                error = state.sheetError,
                onSave = model::save,
                onSkip = model::skip,
                onDelete = model::delete,
                onDismiss = model::closeSheet,
            )
        }
    }
    state.preview?.let { preview ->
        ImportSheet(preview.result, busy = state.busy, onImport = model::confirmImport, onDismiss = model::closePreview)
    }
}

private fun LazyListScope.month(state: CalendarState, model: CalendarViewModel, onOpenLink: (String) -> Unit) {
    item(key = "month-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { model.moveMonth(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
            Text(
                monthText(state.month),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { model.moveMonth(1) }) { Icon(Icons.Outlined.ChevronRight, "Next month") }
            TextButton(onClick = model::goToday) { Text("Today") }
        }
    }
    item(key = "grid") { MonthGrid(state, model::select) }
    item(key = "day-heading") {
        Text(
            dayHeading(state.selected, state.today),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    when (val view = state.view) {
        LoadState.Loading -> item(key = "loading") { Skeleton(lines = 3, lineHeight = 40.dp) }
        is LoadState.Failed -> item(key = "error") { ErrorState(view.message, model::refresh) }
        is LoadState.Loaded -> {
            val items = itemsOn(view.value.items, state.selected)
            if (items.isEmpty()) {
                item(key = "empty") { Text("Nothing on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(items, key = { "day-" + it.id }) { item -> ItemRow(item, state.selected, model, onOpenLink) }
            }
        }
    }
}

private fun LazyListScope.agenda(state: CalendarState, model: CalendarViewModel, onOpenLink: (String) -> Unit) {
    when (val view = state.view) {
        LoadState.Loading -> item(key = "loading") { Skeleton(lines = 5, lineHeight = 40.dp) }
        is LoadState.Failed -> item(key = "error") { ErrorState(view.message, model::refresh) }
        is LoadState.Loaded -> {
            val days = generateSequence(state.today) { it.plusDays(1) }.take(AGENDA_DAYS.toInt())
                .map { it to itemsOn(view.value.items, it) }.filter { it.second.isNotEmpty() }.toList()
            if (days.isEmpty()) {
                item(key = "empty") { Text("Nothing in the next 60 days.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            days.forEach { (day, items) ->
                item(key = "agenda-$day") {
                    Text(
                        dayHeading(day, state.today).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (day == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(items, key = { "agenda-$day-" + it.id }) { item -> ItemRow(item, day, model, onOpenLink) }
            }
        }
    }
}

/** The month in whole weeks: each day with its number and up to four dots, one per item, in the item's colour. */
@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val items = state.items
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf(7, 1, 2, 3, 4, 5, 6).forEach { dayOfWeek ->
                Text(
                    java.time.DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.NARROW, Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        monthWeeks(state.month).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = day.month == state.month.month,
                        today = day == state.today,
                        selected = day == state.selected,
                        items = itemsOn(items, day),
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    selected: Boolean,
    items: List<CalendarItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = "${dayHeading(day, day.plusDays(2))}${if (items.isNotEmpty()) ", ${items.size} item${if (items.size == 1) "" else "s"}" else ""}"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(52.dp)
            .background(if (inMonth) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .padding(top = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape)
                .background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent),
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    today -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.take(4).forEach { item ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(accentOf(accentId(item))?.color ?: MaterialTheme.colorScheme.primary))
            }
        }
    }
}

/**
 * One item: its icon in the module's colour, the title, when it is (and the place, "repeats"), and the module. A tap
 * opens an event's sheet, or the page of another module's item.
 */
@Composable
private fun ItemRow(item: CalendarItem, day: LocalDate, model: CalendarViewModel, onOpenLink: (String) -> Unit) {
    val accent = accentOf(accentId(item))
    val color = accent?.color ?: MaterialTheme.colorScheme.primary
    val meta = listOfNotNull(whenText(item), item.text, if (item.recurring) "repeats" else null).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) {
                val eventId = item.eventId
                when {
                    eventId != null -> model.openEvent(eventId, if (item.recurring) calendarDay(item.startDate) ?: day else null)
                    item.link != null -> onOpenLink(item.link!!)
                }
            }
            .padding(start = 0.dp, end = 12.dp),
    ) {
        Box(Modifier.width(4.dp).height(56.dp).background(color))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(accent?.container ?: MaterialTheme.colorScheme.primaryContainer),
        ) { Icon(iconOf(item), null, tint = color, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done == true) TextDecoration.LineThrough else null,
            )
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text(moduleLabel(item), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
