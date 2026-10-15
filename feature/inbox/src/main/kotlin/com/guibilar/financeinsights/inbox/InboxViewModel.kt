package com.guibilar.financeinsights.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxState(
    val notes: LoadState<List<InboxNote>> = LoadState.Loading,
    val total: Int = 0,
    /** The sorted notes, not the ones to sort. */
    val sorted: Boolean = false,
    /** The quick note being written. */
    val text: String = "",
    val target: NoteTarget = NoteTarget.Inbox,
    /** The id of the note that a change runs on ("new" for the quick note), or null. */
    val busy: String? = null,
    val refreshing: Boolean = false,
)

/** What to tell the person when a change failed. */
internal fun writeError(error: ApiError, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read the Inbox."
    error is ApiError.Http && error.status == 404 -> "This note does not exist any more."
    else -> error.validationOr(error.loadMessage(failure))
}

/** The Inbox (T18): the quick note, the notes to sort (or the sorted ones), and the changes to them. */
class InboxViewModel(private val api: InboxApi, private val sender: NoteSender) : ViewModel() {
    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var shown = false

    init {
        reload()
    }

    fun refresh() = reload(refreshing = true)

    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload()
    }

    fun showSorted(sorted: Boolean) {
        if (sorted == _state.value.sorted) return
        _state.update { it.copy(sorted = sorted, notes = LoadState.Loading) }
        reload()
    }

    fun type(text: String) = _state.update { it.copy(text = text.take(INBOX_TEXT_LIMIT)) }

    fun target(target: NoteTarget) = _state.update { it.copy(target = target) }

    private fun reload(refreshing: Boolean = false) {
        _state.update { it.copy(refreshing = refreshing) }
        val sorted = _state.value.sorted
        viewModelScope.launch {
            val page = load("Could not load the Inbox.") { api.notes(done = sorted) }
            _state.update { state ->
                if (state.sorted != sorted) return@update state
                state.copy(
                    notes = when (page) {
                        is LoadState.Loaded -> LoadState.Loaded(page.value.items)
                        is LoadState.Failed -> if (state.notes is LoadState.Loaded) state.notes else page
                        LoadState.Loading -> state.notes
                    },
                    total = page.valueOrNull?.totalCount ?: state.total,
                    refreshing = false,
                )
            }
        }
    }

    /** Saves the quick note where the chips say. */
    fun send() {
        val state = _state.value
        val text = state.text.trim()
        if (text.isEmpty() || state.busy != null) return
        val (success, work) = when (state.target) {
            NoteTarget.Inbox -> "Saved to the Inbox" to suspend { api.add(InboxText(text)); Unit }
            NoteTarget.Task -> "Saved to Tasks" to suspend { sender.toTask(text) }
            NoteTarget.Journal -> "Saved to the journal" to suspend { sender.toJournal(text) }
        }
        run("new", success, "Could not save the note.", work) { _state.update { it.copy(text = "") } }
    }

    /** Sorts a note: adds it to the module first, then marks it done with where it went. */
    fun sortTo(note: InboxNote, target: NoteTarget) {
        val success = if (target == NoteTarget.Task) "Added to Tasks" else "Added to the journal"
        run(note.id, success, "Could not sort the note.", {
            if (target == NoteTarget.Task) sender.toTask(note.text) else sender.toJournal(note.text)
            api.done(note.id, InboxDone(target.label))
        })
    }

    fun done(note: InboxNote) = run(note.id, "Done", "Could not change the note.", { api.done(note.id, InboxDone()) })

    fun reopen(note: InboxNote) = run(note.id, "Back in the Inbox", "Could not change the note.", { api.reopen(note.id) })

    fun edit(note: InboxNote, text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        run(note.id, "Saved", "Could not save the note.", { api.update(note.id, InboxText(value.take(INBOX_TEXT_LIMIT))) })
    }

    fun delete(note: InboxNote) = run(note.id, "Deleted the note", "Could not delete the note.", { api.delete(note.id) })

    private fun run(key: String, success: String, failure: String, work: suspend () -> Any?, after: () -> Unit = {}) {
        _state.update { it.copy(busy = key) }
        viewModelScope.launch {
            val result = apiCall { work() }
            _state.update { it.copy(busy = null) }
            val error = result.apiError
            if (error != null) {
                _messages.send(UiMessage(writeError(error, failure)))
                return@launch
            }
            after()
            DataChanges.changed()
            _messages.send(UiMessage(success))
            reload()
        }
    }
}
