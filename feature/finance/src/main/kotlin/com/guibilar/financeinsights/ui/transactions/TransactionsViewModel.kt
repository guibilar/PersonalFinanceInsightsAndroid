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
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.logic.DateChoice
import com.guibilar.financeinsights.core.logic.DateRange
import com.guibilar.financeinsights.core.logic.TransactionLookup
import com.guibilar.financeinsights.core.logic.describeRulesResult
import com.guibilar.financeinsights.core.logic.findTransaction
import com.guibilar.financeinsights.core.logic.linkCriteria
import com.guibilar.financeinsights.core.logic.importSummary
import com.guibilar.financeinsights.core.logic.matchDatePreset
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.CategoryRulePreview
import com.guibilar.financeinsights.core.model.CategoryRulePreviewInput
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.TagInput
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionLink
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.core.model.TransactionBulkUpdate
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The transactions list: its criteria (search, category, tag, account, flow, dates), sorting, pages loaded as the list
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
            // A merchant's link (from Subscriptions) opens on its search.
            search = route.criteria.description.orEmpty(),
            breakdownOpen = route.overTime,
            interval = route.interval ?: BreakdownInterval.Month,
            today = clock(),
        ),
    )
    val state: StateFlow<TransactionsState> = _state.asStateFlow()

    // Kept until shown, so a message isn't lost while the screen is away (rotating, say).
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: kotlinx.coroutines.flow.Flow<UiMessage> = _messages.receiveAsFlow()

    /** Tells the person something (T75: a quick action that a read-only user cannot use). */
    fun say(text: String) {
        _messages.trySend(UiMessage(text))
    }

    // Every text typed, the same one again included (a StateFlow would drop that): see the debounce in init.
    private val searchText = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var listJob: Job? = null
    // Before init, which can start a link.
    /** The linked row that the list must still load (it can be on a later page); null when none. */
    private var pendingLink: String? = null
    private var linkJob: Job? = null
    private val breakdown = TransactionsBreakdown(api, viewModelScope, _state)
    private val statements = StatementImport(
        viewModelScope,
        send = { file, accountId, confirmed -> import(file.name, file.bytes, accountId, confirmed) },
        imported = ::imported,
        partlyImported = ::partlyImported,
        say = { changed(it) },
    )

    /** The import sheet, its files and their uploads. */
    val importState: StateFlow<ImportState> = statements.state

    init {
        // Typing searches after a pause, not on every letter. No distinctUntilChanged: Clear sets the criteria at once,
        // so text typed again just after (the same as before the clear) must still reach them; setCriteria skips no-ops.
        searchText.debounce(SEARCH_DEBOUNCE_MS)
            .onEach { text -> setCriteria { copy(description = text.trim().ifEmpty { null }) } }
            .launchIn(viewModelScope)
        // A link to one transaction finds it first, then lists its day and account. Else the list loads at once.
        val linked = route.transactionId
        if (linked != null) {
            showTransaction(linked)
        } else {
            reloadList()
            breakdown.reload()
        }
        loadLookups()
    }

    /**
     * Shows one transaction: the list changes to its day and account (no other filter, the live list), and its row
     * is highlighted when it is loaded. A deleted one shows the same way in the deleted list, with [LinkNotice.Deleted]
     * (T65). An unknown one, or a failed look-up, leaves the list as it is (or loads the normal list, on a first show)
     * with a [LinkNotice].
     */
    fun showTransaction(id: String) {
        linkJob?.cancel()
        pendingLink = null
        _state.update { it.copy(linkNotice = null, highlighted = null) }
        linkJob = viewModelScope.launch {
            when (val found = api.findTransaction(id)) {
                is TransactionLookup.Found -> {
                    pendingLink = found.transaction.id
                    _state.update { it.copy(criteria = linkCriteria(found.transaction), search = "", showDeleted = false) }
                    reloadList(clear = true)
                    breakdown.reload()
                }
                is TransactionLookup.Deleted -> {
                    pendingLink = found.transaction.id
                    _state.update {
                        it.copy(criteria = linkCriteria(found.transaction), search = "", showDeleted = true, linkNotice = LinkNotice.Deleted)
                    }
                    reloadList(clear = true)
                }
                TransactionLookup.Missing -> notFound(LinkNotice.Missing)
                TransactionLookup.Failed -> notFound(LinkNotice.Failed)
            }
        }
    }

    private fun notFound(notice: LinkNotice) {
        _state.update { it.copy(linkNotice = notice) }
        // On a first show the list waited for the look-up: now the normal list loads.
        if (listJob == null) {
            reloadList()
            breakdown.reload()
        }
    }

    /**
     * After a page loads: highlights the linked row when the list has it, or loads the next page. A failed page keeps
     * the link, so "Try again" goes on with it.
     */
    private fun followLink() {
        val id = pendingLink ?: return
        val list = _state.value.list
        when {
            list.items.any { it.id == id } -> {
                pendingLink = null
                _state.update { it.copy(highlighted = id) }
            }
            list.error != null || list.moreFailed -> {}
            !list.endReached -> loadMore()
            // The day and account do not list it (it changed meanwhile): the list stays, without a highlight.
            else -> pendingLink = null
        }
    }

    /** The screen showed the highlight of [id] for its time. */
    fun highlightShown(id: String) = _state.update { if (it.highlighted == id) it.copy(highlighted = null) else it }

    /** Closes the message about a linked transaction. */
    fun dismissLinkNotice() = _state.update { it.copy(linkNotice = null) }

    /** Another view or other filters: a link that is not done yet, and its message, are no longer for this list. */
    private fun dropLink() {
        linkJob?.cancel()
        pendingLink = null
        _state.update { it.copy(linkNotice = null, highlighted = null) }
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
            val accounts = apiCall { api.accounts() }.getOrNull()
            _state.update { it.copy(accounts = accounts ?: it.accounts, accountsLoaded = true) }
        }
        loadBudgets()
        loadTags()
    }

    /** Again after any change: counts move, and the edit sheet may have added one. */
    private fun loadTags() {
        viewModelScope.launch {
            apiCall { api.tags() }.getOrNull()?.let { tags -> _state.update { it.copy(tags = tags) } }
        }
    }

    /** Adds a tag from the edit sheet, by a name no tag has yet; the tag, or why not (a 409 says the name is taken). */
    private suspend fun createTagNow(name: String): Result<Tag> {
        val result = apiCall { api.addTag(TagInput(name)) }
        val error = result.apiError ?: run {
            val tag = result.getOrThrow()
            _state.update { state -> state.copy(tags = (state.tags + tag).sortedBy { it.name.lowercase() }) }
            return Result.success(tag)
        }
        return Result.failure(IllegalStateException(error.conflictOr(error.validationOr(error.loadMessage("Could not add the tag.")))))
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
        val before = _state.value
        if (now == before.today) return
        // A quick date filter means the same thing on the new day: "This month" moves on to the new month.
        val preset = (matchDatePreset(before.criteria, before.today) as? DateChoice.Preset)?.preset
        _state.update { it.copy(today = now) }
        if (preset != null) {
            val range = preset.range(now)
            val moved = before.criteria.copy(startDate = range.startDate, endDate = range.endDate)
            if (moved != before.criteria) {
                setCriteria { moved }
                return
            }
        }
        reloadList()
        breakdown.reload()
    }
    private var shown = false

    /** Lists the deleted transactions instead (to restore one), or back. */
    fun toggleDeleted() {
        dropLink()
        _state.update { it.copy(showDeleted = !it.showDeleted) }
        // The other view's rows mustn't linger meanwhile: tapping one would offer to restore a transaction that isn't deleted.
        reloadList(clear = true)
        breakdown.reload()
    }

    /**
     * The sheets' saves (add, edit, link, unlink, a new tag) run in the view model's scope, not the sheet's: closing
     * the sheet (or rotating) while one is on its way cancels the waiting, not the save, so the list still reloads
     * and says what happened.
     */
    suspend fun add(input: TransactionInput): AddOutcome = outlivingSheet { addNow(input) }
    suspend fun saveEdit(transaction: Transaction, update: TransactionUpdate, rule: CategoryRuleInput?): String? =
        outlivingSheet { saveEditNow(transaction, update, rule) }
    suspend fun link(transaction: Transaction, budget: Budget): String? = outlivingSheet { linkNow(transaction, budget) }
    suspend fun unlink(transaction: Transaction): String? = outlivingSheet { unlinkNow(transaction) }
    suspend fun createTag(name: String): Result<Tag> = outlivingSheet { createTagNow(name) }

    private suspend fun <T> outlivingSheet(block: suspend () -> T): T = viewModelScope.async { block() }.await()

    /** Adds a transaction by hand; [TransactionInput.allowDuplicate] adds it although an identical one exists. */
    private suspend fun addNow(input: TransactionInput): AddOutcome {
        val result = apiCall { api.addTransaction(input) }
        val error = result.apiError ?: run {
            val added = result.getOrThrow()
            // Today asks the server for new cards (not its short cache) the next time it shows.
            DataChanges.changed()
            _state.update { it.copy(lastAdded = LocalDate.parse(input.date) to input.accountId) }
            // "View" shows it in the list (on its day and account, highlighted), as the web's toast does.
            changed("Added “${added.description}” to ${added.accountName}", "View") { showTransaction(added.id) }
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
    private suspend fun saveEditNow(transaction: Transaction, update: TransactionUpdate, rule: CategoryRuleInput?): String? {
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
            // Applying runs every rule: the count is all it changed, not only transactions like this one.
            else "Transaction updated. Rule “${rule.keyword}” added and $updated ${if (updated == 1) "transaction" else "transactions"} updated by the rules",
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
    private suspend fun linkNow(transaction: Transaction, budget: Budget): String? {
        val result = apiCall { api.linkTransaction(TransactionLink(transaction.id, budget.id)) }
        result.apiError?.let { return it.conflictOr(it.loadMessage("Could not link the transaction.")) }
        changed("Linked to “${budget.name}”")
        return null
    }

    private suspend fun unlinkNow(transaction: Transaction): String? {
        val result = apiCall { api.unlinkTransaction(transaction.id) }
        result.apiError?.let { return it.loadMessage("Could not unlink the transaction.") }
        changed("Transaction unlinked")
        return null
    }

    /**
     * Imports a statement into an account; [confirmed] goes ahead although it looks like another account's. Says nothing:
     * when the files are done, [imported] sums them up, or [partlyImported] shows what came in.
     */
    suspend fun import(fileName: String, bytes: ByteArray, accountId: String, confirmed: Boolean): ImportOutcome {
        val result = apiCall { api.importStatement(fileName, bytes, accountId, confirmed) }
        val error = result.apiError ?: run {
            DataChanges.changed()
            return ImportOutcome.Imported(result.getOrThrow())
        }
        return error.accountMismatch?.let { ImportOutcome.Mismatch(it) } ?: ImportOutcome.Failed(error.importMessage())
    }

    /** Opens the import; with [files] shared to the app, ready to go, and [accountId] picked (T63). */
    fun openImport(files: List<PickedFile> = emptyList(), accountId: String? = null) = statements.open(files, accountId)

    fun closeImport() = statements.close()

    /** Files picked in the import sheet; [unreadable] of them couldn't be read. */
    fun pickStatements(files: List<PickedFile>, unreadable: Int = 0) = statements.take(files, unreadable)

    fun chooseImportAccount(id: String) = statements.chooseAccount(id)

    fun importStatements(accountId: String) = statements.importPending(accountId)

    fun importAnyway(file: PickedFile, accountId: String) = statements.importAnyway(file, accountId)

    /** Ticks or unticks one; the first starts selecting. */
    fun toggleSelected(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /** Every transaction loaded so far, ticked. */
    fun selectAll() = _state.update { state -> state.copy(selected = state.list.items.map { it.id }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /**
     * One change for every ticked transaction: a category, a flow, a budget (linked, or unlinked), or a tag (added or
     * removed). Said with what
     * the API skipped (a flow that doesn't fit, say); the ticks go once it is done.
     */
    fun bulk(change: TransactionBulkUpdate) {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // The API takes up to 500 at a time; "Select all" after scrolling far can tick more.
            var updated = 0
            val notes = mutableListOf<String>()
            for (chunk in ids.chunked(BULK_LIMIT)) {
                val result = apiCall { api.bulkUpdate(change.copy(transactionIds = chunk)) }
                val error = result.apiError
                if (error != null) {
                    val why = error.conflictOr(error.validationOr(error.loadMessage("Could not change the transactions.")))
                    // Earlier batches went through: the list shows them, and the message says how far it got.
                    if (updated > 0) changed("Updated $updated, then stopped: $why") else _messages.trySend(UiMessage(why))
                    return@launch
                }
                val done = result.getOrThrow()
                updated += done.updated
                notes += done.notes
            }
            clearSelection()
            changed((listOf("Updated $updated ${if (updated == 1) "transaction" else "transactions"}") + notes.distinct()).joinToString(". "))
        }
    }

    /** Re-applies the category and flow rules to the stored transactions. */
    fun applyRules() {
        viewModelScope.launch {
            val result = apiCall { api.applyRules() }
            result.getOrNull()?.let { changed(describeRulesResult(it)) }
                ?: _messages.trySend(UiMessage(result.apiError!!.loadMessage("Could not apply the rules.")))
        }
    }

    /** Every file picked is in. */
    fun imported(results: List<ImportResult>) = changed(importSummary(results))

    /** Some files are in, others need another look: the list shows what came in, without a word yet. */
    fun partlyImported() {
        reloadList()
        breakdown.reload()
    }

    /** Something changed on the server: the list and totals follow, and the person is told. */
    private fun changed(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        reloadList()
        breakdown.reload()
        loadTags()
        _messages.trySend(UiMessage(message, actionLabel, action))
    }

    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchText.tryEmit(text)
    }

    fun setCategory(id: String?) = setCriteria { copy(categoryId = id) }
    fun setTag(id: String?) = setCriteria { copy(tagId = id) }
    fun setAccount(id: String?) = setCriteria { copy(accountId = id) }
    fun setFlow(flow: Flow?) = setCriteria { copy(flow = flow) }
    fun setDates(range: DateRange) = setCriteria { copy(startDate = range.startDate, endDate = range.endDate) }

    /** Clears everything but the search field's text, which is cleared too. */
    fun clearAll() {
        _state.update { it.copy(search = "") }
        searchText.tryEmit("")
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
        breakdown.reload()
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
            followLink()
        }
    }

    fun toggleBreakdown() = _state.update { it.copy(breakdownOpen = !it.breakdownOpen) }

    fun setInterval(interval: BreakdownInterval) {
        // Against the interval shown (the API may chart a long range by a longer one), not only the one asked for.
        val shown = _state.value.breakdown.valueOrNull?.interval ?: _state.value.interval
        if (interval == _state.value.interval && interval == shown) return
        _state.update { it.copy(interval = interval) }
        breakdown.reload()
    }

    /** A period of the chart: list just its transactions, a step closer (a month's weeks, a year's months). */
    fun showPeriod(period: BreakdownPeriod) {
        _state.update { it.copy(interval = closer(it.breakdown.valueOrNull?.interval ?: it.interval)) }
        // The range may already be just that period ("Last month" by month): only the closer look changes then.
        if (!changeCriteria { copy(startDate = period.start.take(10), endDate = period.end.take(10)) }) breakdown.reload()
    }

    fun retryBreakdown() = breakdown.reload()

    /**
     * A bar tapped in "Over time" (null: tapped again, to clear): "Where it went" follows, listing that period's
     * merchants instead of the whole range's.
     */
    fun selectPeriod(index: Int?) = breakdown.selectPeriod(index)

    fun loadMoreMerchants() = breakdown.loadMoreMerchants()

    fun showFewerMerchants() = breakdown.showFewerMerchants()


    private fun setCriteria(change: TransactionCriteria.() -> TransactionCriteria) {
        changeCriteria(change)
    }

    /** Applies new criteria; false when they are the same, and nothing was loaded again. */
    private fun changeCriteria(change: TransactionCriteria.() -> TransactionCriteria): Boolean {
        val before = _state.value.criteria
        val after = before.change()
        if (after == before) return false
        dropLink()
        _state.update { it.copy(criteria = after) }
        reloadList()
        breakdown.reload()
        return true
    }

    /** The first page again; the old rows stay (dimmed in the UI) until it arrives, unless [clear]. */
    private fun reloadList(clear: Boolean = false) {
        listJob?.cancel()
        _state.update {
            it.copy(
                list = if (clear) ListState() else it.list.copy(loading = true, error = null, moreFailed = false, loadingMore = false),
                // Other transactions are about to show: what was ticked may not be among them.
                selected = emptySet(),
            )
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
            followLink()
        }
    }

    private fun filter(state: TransactionsState, page: Int) =
        TransactionFilter.of(state.criteria, state.sortBy, state.sortDirection, page, PAGE_SIZE)
            .copy(hidden = if (state.showDeleted) true else null)

    companion object {
        /** The most transactions one bulk change takes (the API's limit). */
        const val BULK_LIMIT = 500

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
