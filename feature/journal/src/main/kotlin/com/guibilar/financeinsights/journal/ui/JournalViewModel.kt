package com.guibilar.financeinsights.journal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.journal.JournalApi
import com.guibilar.financeinsights.core.journal.JournalEntry
import com.guibilar.financeinsights.core.journal.JournalEntrySummary
import com.guibilar.financeinsights.core.journal.JournalSummary
import com.guibilar.financeinsights.core.journal.JournalText
import com.guibilar.financeinsights.core.journal.journalDay
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How many entries the list shows; a search finds older ones. */
const val JOURNAL_LIST_SIZE = 50

/** What to tell the person when a write failed: a read-only user, the server's reasons, or [failure]. */
internal fun writeError(error: ApiError, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read the journal."
    error is ApiError.Http && error.status == 404 -> "This entry does not exist any more."
    error is ApiError.Http && error.status == 409 -> "The entry was just written somewhere else. Load it again."
    else -> error.validationOr(error.loadMessage(failure))
}

data class JournalState(
    val summary: LoadState<JournalSummary> = LoadState.Loading,
    /** Today, as the server counts it (the summary). */
    val today: LocalDate = LocalDate.now(),
    /** The day of the editor. */
    val day: LocalDate = today,
    /** The entry of [day]: null when the day has none. */
    val entry: LoadState<JournalEntry?> = LoadState.Loading,
    /** The text in the editor. */
    val text: String = "",
    val busy: Boolean = false,
    val entries: LoadState<List<JournalEntrySummary>> = LoadState.Loading,
    val totalCount: Int = 0,
    val search: String = "",
    val refreshing: Boolean = false,
) {
    val isToday: Boolean get() = day == today

    /** The editor text differs from the saved entry (trimmed, as the server keeps it). */
    val changed: Boolean get() = text.trim() != (entry.valueOrNull?.text ?: "") && entry is LoadState.Loaded
}

/**
 * The Journal screen (T21): the summary (streak, week, today's entry, "On this day"), the entry of one day in an editor,
 * and the entries (a search filters them). [startDay] opens another day than today (a Today link).
 */
class JournalViewModel(
    private val api: JournalApi,
    startDay: String? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(JournalState(today = today()))
    val state: StateFlow<JournalState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var asked: LocalDate? = journalDay(startDay)
    private var searchJob: Job? = null
    private var entryJob: Job? = null
    private var shown = false

    init {
        reload()
    }

    fun refresh() = reload(refreshing = true)

    /** Back on the screen: everything again, the editor text kept while it has changes. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    private fun reload(quiet: Boolean = false, refreshing: Boolean = false) {
        if (!quiet) _state.update { it.copy(refreshing = refreshing) }
        viewModelScope.launch {
            val loaded = load("Could not load the journal.") { api.summary() }
            val serverToday = journalDay(loaded.valueOrNull?.date)
            _state.update { state ->
                val today = serverToday ?: state.today
                val day = (asked ?: state.day.takeIf { state.summary is LoadState.Loaded } ?: today).let { if (it > today) today else it }
                state.copy(
                    summary = if (loaded is LoadState.Failed && quiet && state.summary is LoadState.Loaded) state.summary else loaded,
                    today = today,
                    day = day,
                    refreshing = false,
                )
            }
            asked = null
            loadEntry(keepText = quiet)
            loadEntries()
        }
    }

    /** Opens the editor on [day] (a day after today opens today). */
    fun openDay(day: LocalDate) {
        val today = _state.value.today
        _state.update { it.copy(day = if (day > today) today else day) }
        loadEntry(keepText = false)
    }

    fun edit(text: String) = _state.update { it.copy(text = text.take(com.guibilar.financeinsights.core.journal.JournalLimits.TEXT)) }

    private fun loadEntry(keepText: Boolean) {
        val state = _state.value
        entryJob?.cancel()
        // Today's entry comes with the summary; another day's is read.
        if (state.isToday && state.summary is LoadState.Loaded) {
            setEntry(LoadState.Loaded(state.summary.value.today), keepText)
            return
        }
        _state.update { it.copy(entry = LoadState.Loading) }
        val day = state.day
        entryJob = viewModelScope.launch {
            val result = apiCall { api.entry(day.toString()) }
            val entry: LoadState<JournalEntry?> = when {
                result.isSuccess -> LoadState.Loaded(result.getOrThrow())
                (result.apiError as? ApiError.Http)?.status == 404 -> LoadState.Loaded(null)
                else -> LoadState.Failed(result.apiError?.loadMessage("Could not load the entry.") ?: "Could not load the entry.")
            }
            if (_state.value.day == day) setEntry(entry, keepText)
        }
    }

    private fun setEntry(entry: LoadState<JournalEntry?>, keepText: Boolean) = _state.update { state ->
        val keep = keepText && state.changed
        state.copy(entry = entry, text = if (keep) state.text else entry.valueOrNull?.text.orEmpty())
    }

    fun search(text: String) {
        _state.update { it.copy(search = text.take(com.guibilar.financeinsights.core.journal.JournalLimits.SEARCH)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadEntries()
        }
    }

    private suspend fun loadEntries() {
        val q = _state.value.search.trim().ifEmpty { null }
        val page = load("Could not load the entries.") { api.entries(q = q, pageSize = JOURNAL_LIST_SIZE) }
        _state.update { state ->
            // A reload's list that lands after a newer search is for the old text: that search sets the list.
            if (state.search.trim().ifEmpty { null } != q) return@update state
            state.copy(
                entries = when (page) {
                    is LoadState.Loaded -> LoadState.Loaded(page.value.items)
                    is LoadState.Failed -> if (state.entries is LoadState.Loaded) state.entries else page
                    LoadState.Loading -> state.entries
                },
                totalCount = page.valueOrNull?.totalCount ?: state.totalCount,
            )
        }
    }

    /** Writes the editor text as the whole entry of the day. */
    fun save() {
        val state = _state.value
        val text = state.text.trim()
        if (text.isEmpty() || state.busy || !state.changed) return
        write(success = "Saved", failure = "Could not save the entry.") { api.save(state.day.toString(), JournalText(text)) }
    }

    fun delete() {
        val state = _state.value
        if (state.busy || state.entry.valueOrNull == null) return
        write(success = "Deleted the entry", failure = "Could not delete the entry.") { api.delete(state.day.toString()) }
    }

    /** One write: busy while it runs; then everything again, Today fresh ([DataChanges]), and [success]. */
    private fun write(success: String, failure: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { block() }
            _state.update { it.copy(busy = false) }
            val error = result.apiError
            if (error != null) {
                _messages.send(UiMessage(writeError(error, failure)))
                return@launch
            }
            DataChanges.changed()
            _messages.send(UiMessage(success))
            reload(quiet = false)
        }
    }
}
