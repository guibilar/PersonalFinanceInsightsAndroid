package com.guibilar.financeinsights.ui.transactions

import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.model.BreakdownInput
import com.guibilar.financeinsights.core.model.MerchantsInput
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Over time" and "Where it went" on the transactions page: the totals of what the criteria pick, the bar tapped in
 * the chart, and the merchants a page at a time. Part of [TransactionsViewModel], in its state.
 */
internal class TransactionsBreakdown(
    private val api: FinanceApi,
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<TransactionsState>,
) {
    private var breakdownJob: Job? = null
    private var merchantsJob: Job? = null

    fun reload() {
        breakdownJob?.cancel()
        // Deleted transactions have no totals: nothing counts them.
        if (stateFlow.value.showDeleted) return
        merchantsJob?.cancel()
        // The old totals stay until the new ones arrive (the strip dims meanwhile in the UI).
        // New periods: a selected bar no longer means the same one.
        stateFlow.update {
            it.copy(
                moreMerchants = emptyList(), loadingMerchants = false, merchantsFailed = false, breakdownLoading = true,
                selectedPeriod = null, periodMerchants = emptyList(), periodMerchantCount = 0,
            )
        }
        breakdownJob = scope.launch {
            val current = stateFlow.value
            val result = load("Could not total these transactions.") { api.breakdown(BreakdownInput.of(current.criteria, current.interval)) }
            // A bar tapped on the old chart while this loaded named a period of the old one.
            merchantsJob?.cancel()
            stateFlow.update {
                it.copy(
                    breakdown = result, breakdownLoading = false,
                    selectedPeriod = null, periodMerchants = emptyList(), periodMerchantCount = 0, loadingMerchants = false,
                )
            }
        }
    }

    /**
     * A bar tapped in "Over time" (null: tapped again, to clear): "Where it went" follows, listing that period's
     * merchants instead of the whole range's.
     */
    fun selectPeriod(index: Int?) {
        merchantsJob?.cancel()
        stateFlow.update {
            it.copy(selectedPeriod = index, periodMerchants = emptyList(), periodMerchantCount = 0, loadingMerchants = false, merchantsFailed = false)
        }
        if (index != null) loadMoreMerchants()
    }

    fun loadMoreMerchants() {
        val current = stateFlow.value
        val breakdown = current.breakdown.valueOrNull ?: return
        val period = current.period
        // A period's first page is asked for when it is selected; otherwise the breakdown brought it.
        val firstPage = period != null && current.periodMerchants.isEmpty()
        if (current.loadingMerchants || (!firstPage && current.merchantsLeft <= 0)) return
        stateFlow.update { it.copy(loadingMerchants = true, merchantsFailed = false) }
        merchantsJob = scope.launch {
            val page = current.merchants.size / TransactionsViewModel.MERCHANTS_PAGE_SIZE + 1
            val criteria = period?.let { current.criteria.copy(startDate = it.start.take(10), endDate = it.end.take(10)) } ?: current.criteria
            val result = apiCall { api.merchants(MerchantsInput.of(criteria, page, TransactionsViewModel.MERCHANTS_PAGE_SIZE)) }
            stateFlow.update { state ->
                // The criteria or the selected bar changed while it loaded: these are another list's merchants.
                if (state.breakdown.valueOrNull !== breakdown || state.period != period) return@update state.copy(loadingMerchants = false)
                result.fold(
                    onSuccess = {
                        if (period != null) {
                            state.copy(periodMerchants = state.periodMerchants + it.items, periodMerchantCount = it.totalCount, loadingMerchants = false)
                        } else {
                            state.copy(moreMerchants = state.moreMerchants + it.items, loadingMerchants = false)
                        }
                    },
                    onFailure = { state.copy(loadingMerchants = false, merchantsFailed = true) },
                )
            }
        }
    }

    fun showFewerMerchants() = stateFlow.update {
        it.copy(moreMerchants = emptyList(), periodMerchants = it.periodMerchants.take(TransactionsViewModel.MERCHANTS_PAGE_SIZE), merchantsFailed = false)
    }
}
