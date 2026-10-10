package com.guibilar.financeinsights.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.logic.ReviewPeriod
import com.guibilar.financeinsights.core.model.RecurringReport
import com.guibilar.financeinsights.core.model.ReviewReport
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The recurring payments: loaded on opening, again on pulling down or coming back. */
class RecurringViewModel(private val api: FinanceApi) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<RecurringReport>>(LoadState.Loading)
    val state: StateFlow<LoadState<RecurringReport>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val result = load("Could not find the recurring payments.") { api.recurring() }
            // A failed refresh keeps what was showing.
            if (result !is LoadState.Failed || _state.value !is LoadState.Loaded) _state.value = result
            _refreshing.value = false
        }
    }

    fun refresh() {
        _refreshing.value = true
        reload()
    }

    /** Back from a payment's transactions, another tab or the background: what changed there counts. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload()
    }
    private var shown = false
}

/** A review: [period] (null: the last complete month), stepping back and forth. */
class ReviewViewModel(
    private val api: FinanceApi,
    initial: ReviewPeriod? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<ReviewReport>>(LoadState.Loading)
    val state: StateFlow<LoadState<ReviewReport>> = _state.asStateFlow()
    /** Another period on its way: the one showing stays (dimmed), so the switcher keeps TalkBack's focus. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private var period: ReviewPeriod? = initial
    private var job: Job? = null

    init {
        reload()
    }

    /** What the shown review is of, once it is here. */
    val shown: ReviewPeriod? get() = (_state.value as? LoadState.Loaded)?.value?.let { ReviewPeriod(it.year, it.month) }

    fun previous() = from()?.step(-1, today(), earliest())
    fun next() = from()?.step(1, today(), earliest())

    // From the one shown (the API's answer), but while another is on its way from that one: two quick taps go
    // two steps, not the same one twice.
    private fun from() = if (_loading.value) period ?: shown else shown

    private fun earliest() = (_state.value as? LoadState.Loaded)?.value?.earliest

    fun go(to: ReviewPeriod?) {
        if (to == null) return
        period = to
        reload()
    }

    /** The review the screen opens on: the way out of a period that failed to load. */
    fun latest() {
        period = null
        reload()
    }

    /** Back from Subscriptions, another tab or the background: what changed there counts. */
    fun resumed() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        reload()
    }
    private var resumedBefore = false

    /** The other kind of review over the same time: this month's year, or that year's last month there is. */
    fun switchTo(year: Boolean) {
        val now = shown ?: return
        if (year == now.isYear) return
        go(if (year) ReviewPeriod(now.year, null) else ReviewPeriod(now.year, if (now.year == today().year) today().monthValue else 12))
    }

    fun reload() {
        job?.cancel()
        if (_state.value is LoadState.Loaded) _loading.value = true else _state.value = LoadState.Loading
        job = viewModelScope.launch {
            _state.value = load("Could not load the review.") { api.review(period?.year, period?.month) }
            _loading.value = false
        }
    }
}
