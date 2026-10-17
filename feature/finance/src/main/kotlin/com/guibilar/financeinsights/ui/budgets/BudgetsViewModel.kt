package com.guibilar.financeinsights.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.BudgetForecast
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.ui.common.UiMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetsState(
    /** What is typed in the search field; searched after a pause. */
    val search: String = "",
    val name: String? = null,
    /** Null: active and inactive. */
    val status: Status? = null,
    val items: List<Budget> = emptyList(),
    val totalCount: Int = 0,
    val pagesLoaded: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** A later page failed: the list stays, with a retry at its end. */
    val moreFailed: Boolean = false,
    val refreshing: Boolean = false,
    val categories: List<CategoryReference> = emptyList(),
    /** Where each monthly and yearly budget should end at its usual pace, by id; none while unknown. */
    val projections: Map<String, BudgetForecast> = emptyMap(),
) {
    val endReached get() = pagesLoaded > 0 && items.size >= totalCount
    val hasCriteria get() = name != null || status != null
}

/** The budgets: searched by name, filtered by status, a page at a time; added, edited and deleted. */
@OptIn(FlowPreview::class)
class BudgetsViewModel(private val api: FinanceApi) : ViewModel() {
    private val _state = MutableStateFlow(BudgetsState())
    val state: StateFlow<BudgetsState> = _state.asStateFlow()

    // Kept until shown, so a message isn't lost while the screen is away (rotating, say).
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    // Every text typed, the same one again included (a StateFlow would drop that): see the debounce in init.
    private val searchText = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var listJob: Job? = null

    init {
        // Compared with the name searched, not the last text: Clear resets the name at once, and the same text typed
        // again just after must search again (and the clear's own "" mustn't load the list twice).
        searchText.debounce(SEARCH_DEBOUNCE_MS)
            .onEach { text ->
                val name = text.trim().ifEmpty { null }
                if (name == _state.value.name) return@onEach
                _state.update { it.copy(name = name) }
                reload()
            }
            .launchIn(viewModelScope)
        reload()
        loadCategories()
    }

    /** The projections follow every reload of the list: spending changes them. A failure keeps the last ones. */
    private fun loadProjections() {
        viewModelScope.launch {
            apiCall { api.forecast() }.getOrNull()?.let { forecast ->
                _state.update { it.copy(projections = forecast.budgets.associateBy { b -> b.budgetId }) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            apiCall { api.categories() }.getOrNull()?.let { categories -> _state.update { it.copy(categories = categories.map { c -> c.reference }) } }
        }
    }

    /**
     * The screen is back (from another tab): what is spent changes as transactions are added, edited or linked
     * elsewhere, and categories under More. The first time is the screen opening, just after init loaded it all.
     */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
        loadCategories()
    }
    private var shown = false

    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchText.tryEmit(text)
    }

    fun setStatus(status: Status?) {
        if (status == _state.value.status) return
        _state.update { it.copy(status = status) }
        reload()
    }

    fun clearAll() {
        _state.update { it.copy(search = "", name = null, status = null) }
        searchText.tryEmit("")
        reload()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        listJob = viewModelScope.launch {
            val result = apiCall { api.budgets(filter(current, current.pagesLoaded + 1)) }
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            items = (state.items + page.items).distinctBy { it.id },
                            totalCount = page.totalCount,
                            pagesLoaded = page.page,
                            loadingMore = false,
                        )
                    },
                    // It used to stop there without a word, the rest of the budgets out of reach.
                    onFailure = { state.copy(loadingMore = false, moreFailed = true) },
                )
            }
        }
    }

    /**
     * Adds a budget, or saves [editing]; null when done, or why not (a category another budget counts, say). In the view
     * model's scope, not the form's: closing the form while it saves cancels the waiting, not the save, so the list still
     * reloads and says so.
     */
    suspend fun save(editing: Budget?, input: BudgetInput): String? = viewModelScope.async { saveNow(editing, input) }.await()

    private suspend fun saveNow(editing: Budget?, input: BudgetInput): String? {
        val result = apiCall { if (editing == null) api.addBudget(input) else api.updateBudget(editing.id, input) }
        result.apiError?.let { error ->
            val fallback = if (editing == null) "Could not add the budget." else "Could not save the budget."
            return error.conflictOr(error.validationOr(error.loadMessage(fallback)))
        }
        reload()
        _messages.trySend(UiMessage(if (editing == null) "Added “${input.name}”" else "Saved “${input.name}”"))
        return null
    }

    fun delete(budget: Budget) {
        viewModelScope.launch {
            val result = apiCall { api.deleteBudget(budget.id) }
            if (result.isSuccess) reload()
            _messages.trySend(
                UiMessage(if (result.isSuccess) "Deleted “${budget.name}”" else result.apiError!!.loadMessage("Could not delete “${budget.name}”.")),
            )
        }
    }

    /** The first page again. [quiet] (back on the screen) keeps the list showing when the reload fails. */
    private fun reload(quiet: Boolean = false) {
        loadProjections()
        listJob?.cancel()
        _state.update { it.copy(loading = true, error = null, loadingMore = false, moreFailed = false) }
        listJob = viewModelScope.launch {
            val current = _state.value
            val result = apiCall { api.budgets(filter(current, 1)) }
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(items = page.items, totalCount = page.totalCount, pagesLoaded = 1, loading = false, refreshing = false)
                    },
                    onFailure = {
                        if (quiet && state.items.isNotEmpty()) return@fold state.copy(loading = false, refreshing = false)
                        state.copy(
                            items = emptyList(), pagesLoaded = 0, loading = false, refreshing = false,
                            error = result.apiError?.loadMessage(LOAD_FAILED) ?: LOAD_FAILED,
                        )
                    },
                )
            }
        }
    }

    private fun filter(state: BudgetsState, page: Int) = BudgetFilter(name = state.name, status = state.status, page = page, pageSize = PAGE_SIZE)

    companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOAD_FAILED = "Could not load budgets."
    }
}
