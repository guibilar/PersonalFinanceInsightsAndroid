package com.guibilar.financeinsights.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.HomeSummary
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.Maintenance
import com.guibilar.financeinsights.core.home.RoomDetails
import com.guibilar.financeinsights.core.home.WarrantyFilter
import com.guibilar.financeinsights.core.home.homeDay
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the item list of the Home screen shows in place of the rooms: a search, or the warranties that end soon. */
data class ItemQuery(val text: String = "", val endingOnly: Boolean = false) {
    val isActive get() = text.isNotBlank() || endingOnly
}

data class HomeState(
    val summary: LoadState<HomeSummary> = LoadState.Loading,
    /** All maintenance from the server (T89): open tasks linked to an item from either side, the next due first. */
    val maintenance: LoadState<List<Maintenance>> = LoadState.Loading,
    /** Today, as the server counts it (the summary). */
    val today: LocalDate = LocalDate.now(),
    val refreshing: Boolean = false,
    val query: ItemQuery = ItemQuery(),
    /** The items of [query]. */
    val results: LoadState<List<ItemSummary>> = LoadState.Loaded(emptyList()),
    /** The export is under way. */
    val exporting: Boolean = false,
    /** A write is under way (a new room). */
    val busy: Boolean = false,
)

/**
 * The Home screen: the summary (count, value, rooms), the maintenance jobs (`GET /api/home/maintenance`), the search over all items,
 * new rooms, and the export for insurance. After each write, Today loads again from the server ([DataChanges]).
 */
class HomeViewModel(
    private val api: HomeApi,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(today = today()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var searchJob: Job? = null
    private var shown = false

    init {
        reload()
    }

    /** Pull to refresh, or "Try again". */
    fun refresh() = reload(refreshing = true)

    /** Back on the screen (after an item or a room changed): the numbers again, kept if that fails. */
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
            coroutineScope {
                val summary = async { load(LOAD_FAILED) { api.summary() } }
                val jobs = async { load("Could not load the maintenance jobs.") { api.maintenance() } }
                val loaded = summary.await()
                val maintenance = jobs.await()
                _state.update { state ->
                    state.copy(
                        // A failed quiet reload keeps what is on screen.
                        summary = if (loaded is LoadState.Failed && quiet && state.summary is LoadState.Loaded) state.summary else loaded,
                        maintenance = if (maintenance is LoadState.Failed && quiet && state.maintenance is LoadState.Loaded) state.maintenance else maintenance,
                        today = homeDay(loaded.valueOrNull?.date) ?: state.today,
                        refreshing = false,
                    )
                }
            }
            if (_state.value.query.isActive) search(_state.value.query, now = true, quiet = quiet)
        }
    }

    /** A new search text; the list follows once typing pauses. */
    fun search(text: String) = search(_state.value.query.copy(text = text), now = false)

    /** "Warranties that end soon" on, or off again. */
    fun showEnding(on: Boolean) = search(_state.value.query.copy(endingOnly = on), now = true)

    /** Back to the rooms. */
    fun clearSearch() = search(ItemQuery(), now = true)

    /** [quiet] (a quiet reload) keeps the results showing while it searches, and when it fails. */
    private fun search(query: ItemQuery, now: Boolean, quiet: Boolean = false) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (!query.isActive) {
            _state.update { it.copy(results = LoadState.Loaded(emptyList())) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!now) delay(SEARCH_DEBOUNCE_MS)
            if (!quiet) _state.update { it.copy(results = LoadState.Loading) }
            val loaded = load("Could not search the items.") {
                api.items(
                    q = query.text.trim().ifEmpty { null },
                    warranty = if (query.endingOnly) WarrantyFilter.EXPIRING else null,
                    pageSize = RESULTS,
                ).items
            }
            _state.update { it.copy(results = if (loaded is LoadState.Failed && quiet && it.results is LoadState.Loaded) it.results else loaded) }
        }
    }

    /** Adds a room; null when done, or why not (for the dialog). */
    suspend fun addRoom(name: String): String? {
        if (_state.value.busy) return null
        _state.update { it.copy(busy = true) }
        val result = apiCall { api.createRoom(RoomDetails(name.trim())) }
        _state.update { it.copy(busy = false) }
        result.apiError?.let { return writeError(it, "Could not add the room.") }
        DataChanges.changed()
        _messages.trySend(UiMessage("Added the room “${result.getOrThrow().name}”"))
        reload(quiet = true)
        return null
    }

    /** The CSV file of all items, or null when it failed (the message says why). */
    suspend fun export(): ByteArray? {
        if (_state.value.exporting) return null
        _state.update { it.copy(exporting = true) }
        val result = apiCall { api.export().use { it.bytes() } }
        _state.update { it.copy(exporting = false) }
        result.apiError?.let {
            _messages.trySend(UiMessage(it.loadMessage("Could not download the list.")))
            return null
        }
        return result.getOrThrow()
    }

    /** Tells the person something (a share that could not open). */
    fun say(text: String) {
        _messages.trySend(UiMessage(text))
    }

    companion object {
        /** The most items a page has (the server's limit). */
        const val PAGE_SIZE = 100

        /** A room loads at most this many pages of items. */
        const val MAX_PAGES = 5

        /** How many items a search shows. */
        const val RESULTS = 50

        const val SEARCH_DEBOUNCE_MS = 350L

        private const val LOAD_FAILED = "Could not load the home."
    }
}

/** What to tell the person when a write failed: a read-only user, the server's reasons, or [failure]. */
internal fun writeError(error: ApiError, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read the home inventory."
    error is ApiError.Http && error.status == 409 -> error.conflictOr(failure)
    else -> error.validationOr(error.loadMessage(failure))
}
