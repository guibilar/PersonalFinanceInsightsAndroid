package com.guibilar.financeinsights.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.accountMismatch
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.api.importMessage
import com.guibilar.financeinsights.core.api.importStatement
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.logic.describeRulesResult
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRulePreview
import com.guibilar.financeinsights.core.model.CategoryRulePreviewInput
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionLink
import com.guibilar.financeinsights.core.model.TransactionUpdate
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
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** The active budgets, to link transactions to. */
    val budgets: List<Budget> = emptyList(),
    /** Deleted transactions are listed instead of the others, to restore one. */
    val showDeleted: Boolean = false,
    /** The day and account of the transaction added by hand just before, which the next one starts on. */
    val lastAdded: Pair<LocalDate, String>? = null,
    val today: LocalDate = LocalDate.now(),
) {
    val merchants get() = (breakdown.valueOrNull?.topMerchants ?: emptyList()) + moreMerchants
    val merchantsLeft get() = (breakdown.valueOrNull?.merchantCount ?: 0) - merchants.size
}

/** How adding a transaction went: an identical one exists (it can still be added on purpose), or another failure. */
sealed interface AddOutcome {
    data object Added : AddOutcome
    data class Duplicate(val message: String) : AddOutcome
    data class Failed(val message: String) : AddOutcome
}

/** How an import went: done, the file looks like another account's (it can still go ahead), or another failure. */
sealed interface ImportOutcome {
    data object Imported : ImportOutcome
    data class Mismatch(val message: String) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}

/**
 * The transactions list: its criteria (search, category, account, flow, dates), sorting, pages loaded as the list
 * scrolls, and the totals and "Over time" breakdown of whatever the criteria pick.
 */
@OptIn(FlowPreview::class)
class TransactionsViewModel(
    private val api: FinanceApi,
    route: TransactionsRoute,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TransactionsState(
            criteria = route.criteria,
            search = "",
            breakdownOpen = route.overTime,
            interval = route.interval ?: BreakdownInterval.Month,
            today = clock(),
        ),
    )
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    // Kept until shown, so a message isn't lost while the screen is away (rotating, say).
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: kotlinx.coroutines.flow.Flow<UiMessage> = _messages.receiveAsFlow()

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
        loadLookups()
    }

    /**
     * The categories, accounts and budgets the pickers offer. Again whenever the screen is back, as they can change
     * under More (a new account to import into); a failed load keeps the ones there were.
     */
    private fun loadLookups() {
        viewModelScope.launch {
            apiCall { api.categories() }.getOrNull()?.let { categories -> _state.update { it.copy(categories = categories) } }
        }
        viewModelScope.launch {
            apiCall { api.accounts() }.getOrNull()?.let { accounts -> _state.update { it.copy(accounts = accounts) } }
        }
        loadBudgets()
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.getOrNull()
            if (budgets != null) _state.update { it.copy(budgets = budgets.items) }
        }
    }

    /**
     * The screen is shown again. A view model outlives a night in the background: on a new day, "today" (the new
     * transaction's date, the date presets) moves on and the list and totals reload.
     */
    fun resumed() {
        // The first time is the screen opening, just after init loaded everything.
        if (!shown) {
            shown = true
            return
        }
        loadLookups()
        val now = clock()
        if (now == _state.value.today) return
        _state.update { it.copy(today = now) }
        reloadList()
        reloadBreakdown()
    }
    private var shown = false

    /** Lists the deleted transactions instead (to restore one), or back. */
    fun toggleDeleted() {
        _state.update { it.copy(showDeleted = !it.showDeleted) }
        // The other view's rows mustn't linger meanwhile: tapping one would offer to restore a transaction that isn't deleted.
        reloadList(clear = true)
        reloadBreakdown()
    }

    /** Adds a transaction by hand; [TransactionInput.allowDuplicate] adds it although an identical one exists. */
    suspend fun add(input: TransactionInput): AddOutcome {
        val result = apiCall { api.addTransaction(input) }
        val error = result.apiError ?: run {
            val added = result.getOrThrow()
            _state.update { it.copy(lastAdded = LocalDate.parse(input.date) to input.accountId) }
            changed("Added “${added.description}” to ${added.accountName}")
            return AddOutcome.Added
        }
        val duplicate = error.conflictOr("")
        return if (duplicate.isNotEmpty()) AddOutcome.Duplicate(duplicate)
        else AddOutcome.Failed(error.validationOr(error.loadMessage("Could not add the transaction.")))
    }

    /**
     * Saves an edit. With [rule], similar transactions follow: the rule goes first, then the rules are applied,
     * with one message for it all. The transaction is saved either way; null when that failed (the message says why).
     */
    suspend fun saveEdit(transaction: Transaction, update: TransactionUpdate, rule: CategoryRuleInput?): String? {
        val saved = apiCall { api.updateTransaction(transaction.id, update) }
        saved.apiError?.let { return it.validationOr(it.loadMessage("Could not update the transaction.")) }
        if (rule == null) {
            changed("Transaction updated")
            return null
        }
        val added = apiCall { api.addCategoryRule(rule) }
        added.apiError?.let {
            changed("Transaction updated, but the rule could not be added: ${it.conflictOr("please try again.")}")
            return null
        }
        val applied = apiCall { api.applyRules() }
        val updated = applied.getOrNull()?.updated
        changed(
            if (updated == null) "Transaction updated and rule “${rule.keyword}” added, but the rules could not be applied."
            else "Transaction updated. Rule “${rule.keyword}” added and $updated other ${if (updated == 1) "transaction" else "transactions"} updated",
        )
        return null
    }

    /** What adding this rule first would change, for the edit form; null while unknown. */
    suspend fun previewRule(keyword: String, categoryId: String, transactionId: String): CategoryRulePreview? =
        apiCall { api.previewCategoryRule(CategoryRulePreviewInput(keyword, categoryId, transactionId)) }.getOrNull()

    /** Deletes it (the API keeps it hidden, so a re-import doesn't add it back), offering to undo. */
    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            val result = apiCall { api.deleteTransaction(transaction.id) }
            if (result.isSuccess) {
                changed("Deleted “${transaction.description}”", "Undo") { restore(transaction) }
            } else {
                _messages.trySend(UiMessage(result.apiError!!.loadMessage("Could not delete the transaction.")))
            }
        }
    }

    fun restore(transaction: Transaction) {
        viewModelScope.launch {
            val result = apiCall { api.restoreTransaction(transaction.id) }
            if (result.isSuccess) changed("Restored “${transaction.description}”")
            else _messages.trySend(UiMessage(result.apiError!!.loadMessage("Could not restore the transaction.")))
        }
    }

    /** Links it to a budget; null when done, or why not (the budget was closed meanwhile, say). */
    suspend fun link(transaction: Transaction, budget: Budget): String? {
        val result = apiCall { api.linkTransaction(TransactionLink(transaction.id, budget.id)) }
        result.apiError?.let { return it.conflictOr(it.loadMessage("Could not link the transaction.")) }
        changed("Linked to “${budget.name}”")
        return null
    }

    suspend fun unlink(transaction: Transaction): String? {
        val result = apiCall { api.unlinkTransaction(transaction.id) }
        result.apiError?.let { return it.loadMessage("Could not unlink the transaction.") }
        changed("Transaction unlinked")
        return null
    }

    /** Imports a statement into an account; [confirmed] goes ahead although it looks like another account's. */
    suspend fun import(fileName: String, bytes: ByteArray, accountId: String, confirmed: Boolean): ImportOutcome {
        val result = apiCall { api.importStatement(fileName, bytes, accountId, confirmed) }
        val error = result.apiError ?: run {
            val imported = result.getOrThrow()
            val noun = if (imported.added == 1) "transaction" else "transactions"
            changed("Imported ${imported.added} new $noun" + if (imported.skipped > 0) " (${imported.skipped} already imported)" else "")
            return ImportOutcome.Imported
        }
        return error.accountMismatch?.let { ImportOutcome.Mismatch(it) } ?: ImportOutcome.Failed(error.importMessage())
    }

    /** Re-applies the category and flow rules to the stored transactions. */
    fun applyRules() {
        viewModelScope.launch {
            val result = apiCall { api.applyRules() }
            result.getOrNull()?.let { changed(describeRulesResult(it)) }
                ?: _messages.trySend(UiMessage(result.apiError!!.loadMessage("Could not apply the rules.")))
        }
    }

    /** Something changed on the server: the list and totals follow, and the person is told. */
    private fun changed(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        reloadList()
        reloadBreakdown()
        _messages.trySend(UiMessage(message, actionLabel, action))
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

    /** The first page again; the old rows stay (dimmed in the UI) until it arrives, unless [clear]. */
    private fun reloadList(clear: Boolean = false) {
        listJob?.cancel()
        _state.update {
            it.copy(list = if (clear) ListState() else it.list.copy(loading = true, error = null, moreFailed = false, loadingMore = false))
        }
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
        // Deleted transactions have no totals: nothing counts them.
        if (_state.value.showDeleted) return
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
            .copy(hidden = if (state.showDeleted) true else null)

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
