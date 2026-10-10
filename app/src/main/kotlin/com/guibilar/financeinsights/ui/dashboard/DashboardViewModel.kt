package com.guibilar.financeinsights.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.Budget
import com.guibilar.financeinsights.core.model.NetWorthReport
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.CashFlowReport
import com.guibilar.financeinsights.core.model.CategorizedReport
import com.guibilar.financeinsights.core.model.Page
import com.guibilar.financeinsights.core.model.SpendingForecast
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardState(
    val today: LocalDate,
    val year: LoadState<CategorizedReport> = LoadState.Loading,
    val month: LoadState<CategorizedReport> = LoadState.Loading,
    val cashFlow: LoadState<CashFlowReport> = LoadState.Loading,
    val budgets: LoadState<Page<Budget>> = LoadState.Loading,
    val recent: LoadState<Page<Transaction>> = LoadState.Loading,
    val forecast: LoadState<SpendingForecast> = LoadState.Loading,
    val netWorth: LoadState<NetWorthReport> = LoadState.Loading,
    /** For what needs attention and the investments; empty while unknown (or when that failed). */
    val accounts: List<Account> = emptyList(),
    /** For subscriptions whose price changed; null while unknown (or when that failed). */
    val recurring: RecurringReport? = null,
    /** Transactions left in Other that a rule could sort; 0 while unknown (or when that failed). */
    val unsorted: Int = 0,
    /** Whether accounts, subscriptions and Other are all in, so what needs attention shows at once. */
    val attentionLoaded: Boolean = false,
    /** Pulled to refresh: the figures stay on screen while they reload. */
    val refreshing: Boolean = false,
)

/** The dashboard's reports, each loading (and failing) on its own, as on the web. */
class DashboardViewModel(private val api: FinanceApi, private val today: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState(today()))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        load()
    }

    /** Shown again: on a new day (the app left overnight) "this month" moves on, and the figures reload. */
    fun resumed() {
        // The first time is the screen opening, just after init loaded it all.
        if (!shown) {
            shown = true
            return
        }
        // Back from another tab or the background: transactions and budgets may have changed there. The figures
        // stay on screen while they reload.
        _state.update { it.copy(today = today()) }
        load(quiet = true)
    }
    private var shown = false
    private var loadJob: Job? = null

    fun refresh() {
        // After midnight into a new month the reports come back for it: "today" moves on with them.
        _state.update { it.copy(refreshing = true, today = today()) }
        load()
    }

    fun retryYear() = retry({ copy(year = it) }) { load("Could not load monthly spending.") { api.yearReport() } }
    fun retryMonth() = retry({ copy(month = it) }) { load("Could not load this month's spending.") { api.monthReport() } }
    fun retryCashFlow() = retry({ copy(cashFlow = it) }) { load("Could not load income and spending.") { api.cashFlow() } }
    fun retryBudgets() = retry({ copy(budgets = it) }) { loadBudgets() }
    fun retryRecent() = retry({ copy(recent = it) }) { loadRecent() }
    fun retryNetWorth() = retry({ copy(netWorth = it) }) { load("Could not load net worth.") { api.netWorth() } }

    /**
     * Loads the reports, each showing as soon as it arrives (it used to wait for the slowest). [quiet] (coming
     * back to the screen) keeps a report that was showing when its reload fails, offline say, instead of turning it
     * into an error card.
     */
    private fun load(quiet: Boolean = false) {
        fun <T> next(old: LoadState<T>, new: LoadState<T>) = if (quiet && new is LoadState.Failed && old is LoadState.Loaded) old else new
        // A load still under way (tabs switched quickly, or pulled to refresh) gives way: its answers are older, and
        // finishing first it would stop the refresh spinner before the new figures are in.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val reports = listOf(
                launch { load("Could not load monthly spending.") { api.yearReport() }.let { r -> _state.update { it.copy(year = next(it.year, r)) } } },
                launch { load("Could not load this month's spending.") { api.monthReport() }.let { r -> _state.update { it.copy(month = next(it.month, r)) } } },
                launch { load("Could not load income and spending.") { api.cashFlow() }.let { r -> _state.update { it.copy(cashFlow = next(it.cashFlow, r)) } } },
                launch { loadBudgets().let { r -> _state.update { it.copy(budgets = next(it.budgets, r)) } } },
                launch { loadRecent().let { r -> _state.update { it.copy(recent = next(it.recent, r)) } } },
                launch { loadForecast().let { r -> _state.update { it.copy(forecast = next(it.forecast, r)) } } },
                launch { load("Could not load net worth.") { api.netWorth() }.let { r -> _state.update { it.copy(netWorth = next(it.netWorth, r)) } } },
                launch {
                    // What needs attention: each part adds nothing when it fails, and the list waits for all of them.
                    val parts = listOf(
                        launch { apiCall { api.uncategorised() }.getOrNull()?.let { r -> _state.update { it.copy(unsorted = r.transactionCount) } } },
                        launch { apiCall { api.accounts() }.getOrNull()?.let { r -> _state.update { it.copy(accounts = r) } } },
                        launch { apiCall { api.recurring() }.getOrNull()?.let { r -> _state.update { it.copy(recurring = r) } } },
                    )
                    parts.joinAll()
                    _state.update { it.copy(attentionLoaded = true) }
                },
            )
            reports.joinAll()
            _state.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun loadBudgets() =
        load("Could not load budgets.") { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }

    private suspend fun loadForecast() = load("Could not work out the forecast.") { api.forecast() }

    fun retryForecast() = retry({ copy(forecast = it) }) { loadForecast() }

    private suspend fun loadRecent() =
        load("Could not load transactions.") { api.transactions(TransactionFilter(page = 1, pageSize = RECENT)) }

    /** Reloads one report, leaving the others as they are. */
    private fun <T> retry(set: DashboardState.(LoadState<T>) -> DashboardState, fetch: suspend () -> LoadState<T>) {
        _state.update { it.set(LoadState.Loading) }
        viewModelScope.launch {
            val result = fetch()
            _state.update { it.set(result) }
        }
    }

    companion object {
        const val RECENT = 5
    }
}
