package com.guibilar.financeinsights.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.Breakdown
import com.guibilar.financeinsights.core.model.BreakdownInput
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.BreakdownPeriod
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.MerchantTotal
import com.guibilar.financeinsights.core.model.MerchantsInput
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.TransactionsRoute
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The list so far, a page at a time. */
data class ListState(
    val items: List<Transaction> = emptyList(),
    val totalCount: Int = 0,
    val pagesLoaded: Int = 0,
    /** The first page is on its way (nothing to show yet, or the criteria changed). */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** Why the first page failed; the list is empty then. */
    val error: String? = null,
    /** A later page failed: the list stays, with a retry at its end. */
    val moreFailed: Boolean = false,
) {
    val endReached get() = pagesLoaded > 0 && items.size >= totalCount
}

data class TransactionsState(
    val criteria: TransactionCriteria,
    /** What is typed in the search field; applied to [criteria] after a pause. */
    val search: String,
    val sortBy: TransactionSortField = TransactionSortField.Date,
    val sortDirection: SortDirection = SortDirection.Descending,
    val list: ListState = ListState(),
    val refreshing: Boolean = false,
    val breakdown: LoadState<Breakdown> = LoadState.Loading,
    /** New totals are on their way; the old ones stay on screen, dimmed. */
    val breakdownLoading: Boolean = true,
    val breakdownOpen: Boolean = false,
    val interval: BreakdownInterval = BreakdownInterval.Month,
    /** Merchants loaded after the breakdown's top ones; a new breakdown starts over. */
    val moreMerchants: List<MerchantTotal> = emptyList(),
    val loadingMerchants: Boolean = false,
    val merchantsFailed: Boolean = false,
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val today: LocalDate = LocalDate.now(),
) {
    val merchants get() = (breakdown.valueOrNull?.topMerchants ?: emptyList()) + moreMerchants
    val merchantsLeft get() = (breakdown.valueOrNull?.merchantCount ?: 0) - merchants.size
}

/**
 * The transactions list: its criteria (search, category, account, flow, dates), sorting, pages loaded as the list
 * scrolls, and the totals and "Over time" breakdown of whatever the criteria pick.
 */
@OptIn(FlowPreview::class)
class TransactionsViewModel(
    private val api: FinanceApi,
    route: TransactionsRoute,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        TransactionsState(
            criteria = route.criteria,
            search = "",
            breakdownOpen = route.overTime,
            interval = route.interval ?: BreakdownInterval.Month,
            today = today,
        ),
    )
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    private val searchText = MutableStateFlow("")
    private var listJob: Job? = null
    private var breakdownJob: Job? = null
    private var merchantsJob: Job? = null

    init {
        // Typing searches after a pause, not on every letter.
        searchText.drop(1).debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()
            .onEach { text -> setCriteria { copy(description = text.trim().ifEmpty { null }) } }
            .launchIn(viewModelScope)
        reloadList()
        reloadBreakdown()
        viewModelScope.launch {
            val categories = apiCall { api.categories() }.getOrNull().orEmpty()
            val accounts = apiCall { api.accounts() }.getOrNull().orEmpty()
            _state.update { it.copy(categories = categories, accounts = accounts) }
        }
    }

    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchText.value = text
    }

    fun setCategory(id: String?) = setCriteria { copy(categoryId = id) }
    fun setAccount(id: String?) = setCriteria { copy(accountId = id) }
    fun setFlow(flow: Flow?) = setCriteria { copy(flow = flow) }
    fun setDates(range: DateRange) = setCriteria { copy(startDate = range.startDate, endDate = range.endDate) }

    /** Clears everything but the search field's text, which is cleared too. */
    fun clearAll() {
        _state.update { it.copy(search = "") }
        searchText.value = ""
        setCriteria { TransactionCriteria() }
    }

    fun clearSearch() {
        search("")
        setCriteria { copy(description = null) }
    }

    /** The same column again flips the direction; another starts the way that column reads best. */
    fun sort(field: TransactionSortField) {
        _state.update {
            val direction = if (it.sortBy == field) {
                if (it.sortDirection == SortDirection.Ascending) SortDirection.Descending else SortDirection.Ascending
            } else {
                DEFAULT_DIRECTION.getValue(field)
            }
            it.copy(sortBy = field, sortDirection = direction)
        }
        reloadList()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reloadList()
        reloadBreakdown()
    }

    /** The next page, when the list is scrolled near its end. */
    fun loadMore() {
        val list = _state.value.list
        if (list.loading || list.loadingMore || list.endReached || list.error != null) return
        _state.update { it.copy(list = it.list.copy(loadingMore = true, moreFailed = false)) }
        listJob = viewModelScope.launch {
            val current = _state.value
            val result = apiCall { api.transactions(filter(current, page = list.pagesLoaded + 1)) }
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            list = state.list.copy(
                                // A row can shift pages if one was added meanwhile; it shows once.
                                items = (state.list.items + page.items).distinctBy { it.id },
                                totalCount = page.totalCount,
                                pagesLoaded = page.page,
                                loadingMore = false,
                            ),
                        )
                    },
                    onFailure = { state.copy(list = state.list.copy(loadingMore = false, moreFailed = true)) },
                )
            }
        }
    }

    fun toggleBreakdown() = _state.update { it.copy(breakdownOpen = !it.breakdownOpen) }

    fun setInterval(interval: BreakdownInterval) {
        if (interval == _state.value.interval) return
        _state.update { it.copy(interval = interval) }
        reloadBreakdown()
    }

    /** A period of the chart: list just its transactions, a step closer (a month's weeks, a year's months). */
    fun showPeriod(period: BreakdownPeriod) {
        _state.update { it.copy(interval = closer(it.breakdown.valueOrNull?.interval ?: it.interval)) }
        setCriteria { copy(startDate = period.start.take(10), endDate = period.end.take(10)) }
    }

    fun retryBreakdown() = reloadBreakdown()

    fun loadMoreMerchants() {
        val current = _state.value
        val breakdown = current.breakdown.valueOrNull ?: return
        if (current.loadingMerchants || current.merchantsLeft <= 0) return
        _state.update { it.copy(loadingMerchants = true, merchantsFailed = false) }
        merchantsJob = viewModelScope.launch {
            val page = current.merchants.size / MERCHANTS_PAGE_SIZE + 1
            val result = apiCall { api.merchants(MerchantsInput.of(current.criteria, page, MERCHANTS_PAGE_SIZE)) }
            _state.update { state ->
                // The criteria changed while it loaded: these are another list's merchants.
                if (state.breakdown.valueOrNull !== breakdown) return@update state.copy(loadingMerchants = false)
                result.fold(
                    onSuccess = { state.copy(moreMerchants = state.moreMerchants + it.items, loadingMerchants = false) },
                    onFailure = { state.copy(loadingMerchants = false, merchantsFailed = true) },
                )
            }
        }
    }

    fun showFewerMerchants() = _state.update { it.copy(moreMerchants = emptyList(), merchantsFailed = false) }

    private fun setCriteria(change: TransactionCriteria.() -> TransactionCriteria) {
        val before = _state.value.criteria
        val after = before.change()
        if (after == before) return
        _state.update { it.copy(criteria = after) }
        reloadList()
        reloadBreakdown()
    }

    private fun reloadList() {
        listJob?.cancel()
        _state.update { it.copy(list = it.list.copy(loading = true, error = null, moreFailed = false, loadingMore = false)) }
        listJob = viewModelScope.launch {
            val current = _state.value
            val result = apiCall { api.transactions(filter(current, page = 1)) }
            _state.update { state ->
                state.copy(
                    refreshing = false,
                    list = result.fold(
                        onSuccess = { page -> ListState(items = page.items, totalCount = page.totalCount, pagesLoaded = 1, loading = false) },
                        onFailure = { ListState(loading = false, error = result.apiError?.loadMessage(LOAD_FAILED) ?: LOAD_FAILED) },
                    ),
                )
            }
        }
    }

    private fun reloadBreakdown() {
        breakdownJob?.cancel()
        merchantsJob?.cancel()
        // The old totals stay until the new ones arrive (the strip dims meanwhile in the UI).
        _state.update { it.copy(moreMerchants = emptyList(), loadingMerchants = false, merchantsFailed = false, breakdownLoading = true) }
        breakdownJob = viewModelScope.launch {
            val current = _state.value
            val result = load("Could not total these transactions.") { api.breakdown(BreakdownInput.of(current.criteria, current.interval)) }
            _state.update { it.copy(breakdown = result, breakdownLoading = false) }
        }
    }

    private fun filter(state: TransactionsState, page: Int) =
        TransactionFilter.of(state.criteria, state.sortBy, state.sortDirection, page, PAGE_SIZE)

    companion object {
        const val PAGE_SIZE = 50
        const val MERCHANTS_PAGE_SIZE = 8
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOAD_FAILED = "Could not load transactions."

        /** Text columns start A→Z; date and amount start with the newest and largest. */
        val DEFAULT_DIRECTION = mapOf(
            TransactionSortField.Date to SortDirection.Descending,
            TransactionSortField.Amount to SortDirection.Descending,
            TransactionSortField.Description to SortDirection.Ascending,
            TransactionSortField.Category to SortDirection.Ascending,
        )

        fun closer(interval: BreakdownInterval) = when (interval) {
            BreakdownInterval.Year, BreakdownInterval.Quarter -> BreakdownInterval.Month
            BreakdownInterval.Month, BreakdownInterval.Week -> BreakdownInterval.Week
        }
    }
}
