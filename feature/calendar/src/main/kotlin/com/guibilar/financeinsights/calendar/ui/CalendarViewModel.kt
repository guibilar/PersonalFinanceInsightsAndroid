package com.guibilar.financeinsights.calendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.calendar.CalendarApi
import com.guibilar.financeinsights.core.calendar.CalendarEvent
import com.guibilar.financeinsights.core.calendar.CalendarEventRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportResult
import com.guibilar.financeinsights.core.calendar.CalendarItem
import com.guibilar.financeinsights.core.calendar.CalendarSkip
import com.guibilar.financeinsights.core.calendar.CalendarView
import com.guibilar.financeinsights.core.calendar.calendarDay
import com.guibilar.financeinsights.core.calendar.importSummary
import com.guibilar.financeinsights.core.calendar.monthWeeks
import com.guibilar.financeinsights.core.calendar.shortDayText
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** How many days the agenda view shows. */
const val AGENDA_DAYS = 60L

/** What to tell the person when a change failed: a read-only user, the server's reasons, or [failure]. */
internal fun calendarError(error: ApiError?, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read the calendar."
    error is ApiError.Http && error.status == 404 -> "This event does not exist any more."
    error != null -> error.validationOr(error.loadMessage(failure))
    else -> failure
}

/** The event sheet: a new event on [day], or [event] (opened from the occurrence on [occurrence], for a repeated one). */
data class EventTarget(val event: CalendarEvent?, val day: LocalDate, val occurrence: LocalDate? = null)

/** An .ics file read, with what its import would do. */
data class ImportPreview(val ics: String, val result: CalendarImportResult)

data class CalendarState(
    val today: LocalDate,
    val month: YearMonth = YearMonth.from(today),
    val selected: LocalDate = today,
    val agenda: Boolean = false,
    val view: LoadState<CalendarView> = LoadState.Loading,
    val refreshing: Boolean = false,
    val target: EventTarget? = null,
    val preview: ImportPreview? = null,
    val busy: Boolean = false,
    val sheetError: String? = null,
) {
    val items: List<CalendarItem> get() = view.valueOrNull?.items.orEmpty()

    /** The modules whose items did not all load. */
    val partial: List<String> get() = view.valueOrNull?.modules.orEmpty().filter { it.status != "Complete" }.map { it.module }
}

/**
 * The Calendar screen (T104): a month in whole weeks with the items of each day and the list of the chosen day; or the
 * agenda (the next 60 days). [startDay] opens another month; [startEvent] opens an event (a Today or Search link).
 */
class CalendarViewModel(
    private val api: CalendarApi,
    startDay: String? = null,
    startEvent: String? = null,
    startAgenda: Boolean = false,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(
        today().let { now ->
            val day = calendarDay(startDay) ?: now
            CalendarState(today = now, month = YearMonth.from(day), selected = day, agenda = startAgenda)
        },
    )
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadedVersion = DataChanges.version
    private var shown = false

    init {
        reload()
        startEvent?.let { openEvent(it, occurrence = null, showDay = startDay == null) }
    }

    /** The days to load: the whole weeks of the month, or the agenda from today. */
    private fun range(state: CalendarState): Pair<LocalDate, LocalDate> =
        if (state.agenda) {
            state.today to state.today.plusDays(AGENDA_DAYS - 1)
        } else {
            val weeks = monthWeeks(state.month)
            weeks.first().first() to weeks.last().last()
        }

    fun refresh() = reload(refreshing = true)

    /** Back on the screen: loads again when the data changed elsewhere (a task done, a person added). */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        if (DataChanges.version != loadedVersion) reload(quiet = true)
    }

    private fun reload(quiet: Boolean = false, refreshing: Boolean = false) {
        loadJob?.cancel()
        val (from, to) = range(_state.value)
        if (!quiet) _state.update { it.copy(refreshing = refreshing, view = if (refreshing) it.view else LoadState.Loading) }
        loadedVersion = DataChanges.version
        loadJob = viewModelScope.launch {
            val loaded = load("Could not load the calendar.") { api.range(from.toString(), to.toString()) }
            _state.update { state ->
                val serverToday = calendarDay(loaded.valueOrNull?.today) ?: state.today
                state.copy(
                    view = if (loaded is LoadState.Failed && quiet && state.view is LoadState.Loaded) state.view else loaded,
                    today = serverToday,
                    refreshing = false,
                )
            }
        }
    }

    fun showAgenda(agenda: Boolean) {
        if (agenda == _state.value.agenda) return
        _state.update { it.copy(agenda = agenda) }
        reload()
    }

    fun moveMonth(months: Long) {
        _state.update {
            val month = it.month.plusMonths(months)
            it.copy(month = month, selected = if (YearMonth.from(it.today) == month) it.today else month.atDay(1))
        }
        reload()
    }

    fun goToday() {
        val state = _state.value
        val month = YearMonth.from(state.today)
        _state.update { it.copy(month = month, selected = it.today) }
        if (month != state.month || state.agenda) {
            _state.update { it.copy(agenda = false) }
            reload()
        }
    }

    fun select(day: LocalDate) {
        if (moveTo(day)) reload()
    }

    /** Selects [day]; true when what is shown must load again: another month (the agenda does not depend on it). */
    private fun moveTo(day: LocalDate): Boolean {
        val month = YearMonth.from(day)
        val moved = month != _state.value.month && !_state.value.agenda
        _state.update { it.copy(selected = day, month = month) }
        return moved
    }

    // ---- Events ----

    fun newEvent(day: LocalDate = _state.value.selected) = _state.update { it.copy(target = EventTarget(null, day), sheetError = null) }

    /** An event of the Calendar opens its sheet; [occurrence] is the day of the occurrence that was tapped. */
    fun openEvent(id: String, occurrence: LocalDate?) = openEvent(id, occurrence, showDay = false)

    /**
     * [showDay]: a link's event (Today, Search) moves to its day, unless the link chose one. Not a repeated one: its
     * first day can be years ago.
     */
    private fun openEvent(id: String, occurrence: LocalDate?, showDay: Boolean) {
        viewModelScope.launch {
            val result = apiCall { api.event(id) }
            result.onSuccess { event ->
                val day = calendarDay(event.startDate) ?: event.startsAt?.let { calendarDay(it.take(10)) }
                if (showDay && event.repeat == null && day != null && moveTo(day)) reload()
                _state.update { it.copy(target = EventTarget(event, occurrence ?: it.selected, occurrence), sheetError = null) }
            }.onFailure { _messages.send(UiMessage(calendarError(result.apiError, "Could not open the event."))) }
        }
    }

    fun closeSheet() = _state.update { it.copy(target = null, sheetError = null) }

    fun save(request: CalendarEventRequest) {
        val target = _state.value.target ?: return
        change(if (target.event == null) "Event added." else "Event saved.", "Could not save the event.") {
            if (target.event == null) api.create(request) else api.update(target.event.id, request)
        }
    }

    /** Skips the occurrence that was tapped. */
    fun skip() {
        val target = _state.value.target ?: return
        val event = target.event ?: return
        val day = target.occurrence ?: return
        change("${event.title} skipped on ${shortDayText(day)}.", "Could not skip the day.") { api.skip(event.id, CalendarSkip(day.toString())) }
    }

    fun delete() {
        val event = _state.value.target?.event ?: return
        change("Event deleted.", "Could not delete the event.") { api.delete(event.id) }
    }

    /**
     * The sheet can be closed (or another opened) while [call] is on its way: the answer then leaves that sheet alone,
     * and a failure is said in a message instead of in a sheet that is gone.
     */
    private fun change(done: String, failure: String, call: suspend () -> Unit) {
        val target = _state.value.target
        _state.update { it.copy(busy = true, sheetError = null) }
        viewModelScope.launch {
            val result = apiCall { call() }
            if (result.isSuccess) {
                DataChanges.changed()
                _state.update { it.copy(busy = false, target = if (it.target === target) null else it.target) }
                _messages.send(UiMessage(done))
                reload(quiet = true)
            } else {
                val why = calendarError(result.apiError, failure)
                val open = _state.value.target === target
                _state.update { it.copy(busy = false, sheetError = if (open) why else it.sheetError) }
                if (!open) _messages.send(UiMessage(why))
            }
        }
    }

    // ---- Import ----

    /** An .ics text (a picked or shared file): shows what its import would do. */
    fun preview(ics: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { api.import(CalendarImportRequest(ics, dryRun = true)) }
            result.onSuccess { preview -> _state.update { it.copy(busy = false, preview = ImportPreview(ics, preview)) } }
                .onFailure {
                    _state.update { it.copy(busy = false) }
                    _messages.send(UiMessage(calendarError(result.apiError, "Could not read the file.")))
                }
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    fun confirmImport() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { api.import(CalendarImportRequest(preview.ics, dryRun = false)) }
            result.onSuccess { done ->
                DataChanges.changed()
                _state.update { it.copy(busy = false, preview = null) }
                _messages.send(UiMessage(importSummary(done)))
                // Shows the first new or changed event.
                val first = done.events.firstOrNull { it.action == "Add" || it.action == "Update" }
                val day = calendarDay(first?.startDate) ?: first?.startsAt?.let { calendarDay(it.take(10)) }
                // One load: a full one for another month, else a quiet one that keeps what is shown when it fails.
                val moved = day != null && moveTo(day)
                reload(quiet = !moved)
            }.onFailure {
                _state.update { it.copy(busy = false) }
                _messages.send(UiMessage(calendarError(result.apiError, "Could not import the file.")))
            }
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _messages.send(UiMessage(text)) }
    }
}
