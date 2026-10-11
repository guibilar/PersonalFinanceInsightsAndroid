package com.guibilar.financeinsights.ui.transactions

import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.Breakdown
import com.guibilar.financeinsights.core.model.BreakdownInterval
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.MerchantTotal
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.common.LoadState
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
    /** The bar tapped in "Over time" (an index into the breakdown's periods): "Where it went" is then that period's. */
    val selectedPeriod: Int? = null,
    /** The selected period's merchants, a page at a time, and how many there are. */
    val periodMerchants: List<MerchantTotal> = emptyList(),
    val periodMerchantCount: Int = 0,
    val categories: List<Category> = emptyList(),
    /** For the tag filter, the edit sheet and the selection bar. */
    val tags: List<Tag> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** The accounts were asked for once (loaded or not): until then, none isn't "no account". */
    val accountsLoaded: Boolean = false,
    /** The active budgets, to link transactions to. */
    val budgets: List<Budget> = emptyList(),
    /** Deleted transactions are listed instead of the others, to restore one. */
    val showDeleted: Boolean = false,
    /** The day and account of the transaction added by hand just before, which the next one starts on. */
    val lastAdded: Pair<LocalDate, String>? = null,
    /** Ticked for a change to many at once; empty when not selecting. */
    val selected: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.now(),
) {
    val period get() = selectedPeriod?.let { breakdown.valueOrNull?.periods?.getOrNull(it) }
    val merchants get() = if (period != null) periodMerchants else (breakdown.valueOrNull?.topMerchants ?: emptyList()) + moreMerchants
    val merchantsLeft get() =
        if (period != null) periodMerchantCount - periodMerchants.size else (breakdown.valueOrNull?.merchantCount ?: 0) - merchants.size
}

/** How adding a transaction went: an identical one exists (it can still be added on purpose), or another failure. */
sealed interface AddOutcome {
    data object Added : AddOutcome
    data class Duplicate(val message: String) : AddOutcome
    data class Failed(val message: String) : AddOutcome
}

/** How an import went: done, the file looks like another account's (it can still go ahead), or another failure. */
sealed interface ImportOutcome {
    data class Imported(val result: ImportResult) : ImportOutcome
    data class Mismatch(val message: String) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}
