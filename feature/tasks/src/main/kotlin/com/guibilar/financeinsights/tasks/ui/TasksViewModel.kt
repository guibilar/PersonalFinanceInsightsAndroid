package com.guibilar.financeinsights.tasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskSections
import com.guibilar.financeinsights.core.tasks.TaskStatus
import com.guibilar.financeinsights.core.tasks.TaskSummary
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.core.tasks.dueText
import com.guibilar.financeinsights.core.tasks.latestDone
import com.guibilar.financeinsights.core.tasks.overdueTasks
import com.guibilar.financeinsights.core.tasks.parseDay
import com.guibilar.financeinsights.core.tasks.quickTask
import com.guibilar.financeinsights.core.tasks.taskSections
import com.guibilar.financeinsights.ui.common.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TasksState(
    /** The open tasks, in the server's list order. */
    val open: List<TaskSummary> = emptyList(),
    /** The last done tasks, the latest first. */
    val done: List<TaskSummary> = emptyList(),
    /** Today, as the server counts it (the summary), so the sections match Today and the alerts. */
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Only the overdue tasks (the alert link "/tasks?due=overdue"), until "Show all". */
    val overdueOnly: Boolean = false,
    /** The tasks with a write under way: their checkboxes wait. */
    val busy: Set<String> = emptySet(),
    /** A quick add is under way. */
    val adding: Boolean = false,
) {
    /** The sections to show. With [overdueOnly], only "Overdue". */
    val sections: TaskSections
        get() = if (overdueOnly) TaskSections(overdue = overdueTasks(open, today)) else taskSections(open, today)

    val isEmpty get() = open.isEmpty() && done.isEmpty()
}

/**
 * The Tasks screen: all open tasks (in sections) and the last done ones; the quick add; done with "Undo". After each
 * write, Today loads again from the server ([DataChanges]).
 */
class TasksViewModel(
    private val api: TasksApi,
    overdueOnly: Boolean = false,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(TasksState(today = today(), overdueOnly = overdueOnly))
    val state: StateFlow<TasksState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var shown = false

    init {
        reload()
    }

    /** Pull to refresh, or "Try again". */
    fun refresh() = reload(refreshing = true)

    /** Back on the screen (after a document, or a change in the sheet): the tasks again, kept if that fails. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    /** The task sheet changed or deleted a task: the lists again, kept if that fails. */
    fun changed() = reload(quiet = true)

    /** "Show all" after the overdue-only view. */
    fun showAll() = _state.update { it.copy(overdueOnly = false) }

    private fun reload(quiet: Boolean = false, refreshing: Boolean = false) {
        loadJob?.cancel()
        if (!quiet) _state.update { it.copy(loading = it.isEmpty || it.error != null, refreshing = refreshing, error = null) }
        loadJob = viewModelScope.launch {
            val result = apiCall { fetch() }
            val error = result.apiError
            val loaded = result.getOrNull()
            _state.update { state ->
                if (loaded != null) {
                    val (open, done, day) = loaded
                    state.copy(open = open, done = done, today = day, loading = false, refreshing = false, error = null)
                } else if (quiet && !state.isEmpty) {
                    // A failed quiet reload keeps the tasks on screen.
                    state.copy(loading = false, refreshing = false)
                } else {
                    state.copy(loading = false, refreshing = false, error = error?.loadMessage(LOAD_FAILED) ?: LOAD_FAILED)
                }
            }
        }
    }

    /** All open tasks (a few pages at most), the last done ones, and the server's today. */
    private suspend fun fetch(): Triple<List<TaskSummary>, List<TaskSummary>, LocalDate> = coroutineScope {
        val counts = async { apiCall { api.summary() }.getOrNull() }
        val done = async { api.tasks(status = TaskStatus.Done, page = 1, pageSize = PAGE_SIZE).items }
        val open = mutableListOf<TaskSummary>()
        var page = 1
        do {
            val loaded = api.tasks(status = TaskStatus.Open, page = page, pageSize = PAGE_SIZE)
            open += loaded.items
            page++
        } while (loaded.hasNextPage && page <= MAX_PAGES)
        val day = parseDay(counts.await()?.date) ?: today()
        Triple(open, latestDone(done.await()), day)
    }

    /**
     * Adds a task from the quick add: [title], due on [dueOn] (or no date). True when it was added (the field then
     * clears); false keeps what was typed.
     */
    suspend fun add(title: String, dueOn: LocalDate?): Boolean {
        val task = quickTask(title, dueOn) ?: return false
        if (_state.value.adding) return false
        _state.update { it.copy(adding = true) }
        val result = apiCall { api.create(task) }
        _state.update { it.copy(adding = false) }
        val error = result.apiError
        if (error != null) {
            _messages.trySend(UiMessage(writeError(error, "Could not add the task.")))
            return false
        }
        val created = result.getOrThrow()
        _state.update { it.copy(open = inListOrder(it.open + created.summary)) }
        DataChanges.changed()
        _messages.trySend(UiMessage("Added “${created.title}”"))
        return true
    }

    /** Marks [task] done. A repeated task moves to its next date. The message offers "Undo". */
    fun done(task: TaskSummary) = write(task.id, "Could not mark the task done.") {
        val updated = api.done(task.id)
        show(updated)
        val next = if (updated.isDone) null else dueText(updated.dueOn, _state.value.today)
        UiMessage(
            if (next == null) "Done: ${updated.title}" else "Done. Next: $next",
            actionLabel = "Undo",
            action = { undo(task.id) },
        )
    }

    /** Undoes the last done of the task: "Undo" in the message, or a done task unchecked. */
    fun undo(id: String) = write(id, "Could not undo.") {
        show(api.undo(id))
        null
    }

    /** The task, after a done or an undo, in the list it now belongs to. */
    private fun show(task: TaskItem) {
        val summary = task.summary
        _state.update { state ->
            val open = state.open.filterNot { it.id == task.id }
            val done = state.done.filterNot { it.id == task.id }
            if (task.isDone) {
                state.copy(open = open, done = latestDone(listOf(summary) + done))
            } else {
                state.copy(open = inListOrder(open + summary), done = done)
            }
        }
        DataChanges.changed()
    }

    /** Runs a write on one task; [action] answers what to tell the person (null for nothing). */
    private fun write(id: String, failure: String, action: suspend () -> UiMessage?) {
        if (id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            val result = apiCall { action() }
            _state.update { it.copy(busy = it.busy - id) }
            val error = result.apiError
            if (error != null) {
                _messages.trySend(UiMessage(writeError(error, failure)))
                // A 409 or a 404: another device changed the task. The lists are out of date.
                if (error is ApiError.Http && (error.status == 409 || error.status == 404)) reload(quiet = true)
            } else {
                result.getOrNull()?.let { _messages.trySend(it) }
            }
        }
    }

    companion object {
        /** The most tasks a page has (the server's limit). */
        const val PAGE_SIZE = 100

        /** The open tasks load at most this many pages (500 tasks). */
        const val MAX_PAGES = 5

        private const val LOAD_FAILED = "Could not load the tasks."
    }
}

/** What to tell the person when a write failed: a read-only user, the server's reasons, or [failure]. */
internal fun writeError(error: ApiError, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read tasks."
    error is ApiError.Http && error.status == 409 -> error.conflictOr(failure)
    else -> error.validationOr(error.loadMessage(failure))
}

/** The server's list order: by due date (tasks without one last), then the manual order, then the oldest first. */
internal fun inListOrder(tasks: List<TaskSummary>): List<TaskSummary> =
    tasks.sortedWith(
        compareBy<TaskSummary> { it.dueOn == null }
            .thenBy { it.dueOn.orEmpty() }
            .thenBy { it.sortOrder == null }
            .thenBy { it.sortOrder ?: 0 }
            .thenBy { it.createdAt.orEmpty() },
    )
