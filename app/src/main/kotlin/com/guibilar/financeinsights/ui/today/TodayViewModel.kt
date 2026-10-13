package com.guibilar.financeinsights.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.today.TodayApi
import com.guibilar.financeinsights.core.today.TodayDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayState(
    /** The last answer of the server; it stays on the screen while the next one loads. */
    val today: TodayDto? = null,
    /** The first load (or a retry with no cards on the screen). */
    val loading: Boolean = true,
    /** Pulled to refresh: the cards stay on the screen while they reload. */
    val refreshing: Boolean = false,
    /** Why the last load that the person asked for failed; null when it did not. */
    val error: String? = null,
) {
    /** The day the cards are for: the server's day, or the phone's until the first answer. */
    fun day(phoneDay: LocalDate): LocalDate = today?.date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: phoneDay
}

/**
 * Today's cards from `GET /api/today`. The server owns the card rules; the screen only formats them. The first load
 * can use the server's short cache. Pull to refresh, a retry, and the first load after the data changed in this app
 * (a statement imported, a transaction added) ask for a new answer (`refresh=true`), as on the web.
 */
class TodayViewModel(private val api: TodayApi, val phoneDay: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val _state = MutableStateFlow(TodayState())
    val state: StateFlow<TodayState> = _state.asStateFlow()

    /** The data version of the last answer: a later version means that the data changed since. */
    private var loadedVersion = DataChanges.version
    private var shown = false
    private var loadJob: Job? = null

    init {
        load(refresh = false)
    }

    /** Shown again (another tab, or back from the background): the cards reload, quietly. */
    fun resumed() {
        // The first time is the screen opening, just after init loaded the cards.
        if (!shown) {
            shown = true
            return
        }
        load(refresh = DataChanges.version != loadedVersion, quiet = true)
    }

    /** Pulled to refresh. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load(refresh = true)
    }

    /** "Retry" on the page, or on a module that failed. */
    fun retry() = load(refresh = true)

    /**
     * Loads the cards. A load still under way gives way to the new one. [quiet] (coming back to the screen) keeps the
     * cards and says nothing when the load fails, offline say.
     */
    private fun load(refresh: Boolean, quiet: Boolean = false) {
        val version = DataChanges.version
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!quiet) _state.update { it.copy(loading = it.today == null, error = null) }
            val result = apiCall { api.today(refresh = if (refresh) true else null) }
            val today = result.getOrNull()
            if (today != null) {
                loadedVersion = version
                _state.update { TodayState(today = today, loading = false) }
            } else {
                val message = result.apiError?.loadMessage(FAILED) ?: FAILED
                _state.update {
                    it.copy(loading = false, refreshing = false, error = if (quiet && it.today != null) it.error else message)
                }
            }
        }
    }

    companion object {
        const val FAILED = "Could not load Today."
    }
}
