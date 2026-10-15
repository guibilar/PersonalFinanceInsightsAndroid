package com.guibilar.financeinsights.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many days one page has. */
const val TIMELINE_PAGE_DAYS = 14

/** The module chips, in the order of the menu: null is all. */
val TIMELINE_FILTERS: List<Pair<String, String?>> = listOf(
    "All" to null,
    "Money" to "Money",
    "Documents" to "Documents",
    "Tasks" to "Tasks",
    "Home" to "Home",
    "People" to "People",
    "Journal" to "Journal",
)

data class TimelineState(
    /** The pages loaded so far, newest first. Empty while the first loads. */
    val pages: List<TimelinePage> = emptyList(),
    /** The module of the chip that is on, or null for all. */
    val module: String? = null,
    val loading: Boolean = true,
    /** The first page failed: the message, with a retry. */
    val error: String? = null,
    /** "Show earlier" failed. */
    val moreFailed: Boolean = false,
    val refreshing: Boolean = false,
) {
    val days: List<TimelineDay> get() = pages.flatMap { it.days }
    val today: String? get() = pages.firstOrNull()?.today

    /** The modules that failed on any page. */
    val failed: List<String> get() = pages.flatMap { it.modules }.filter { it.status == "Failed" }.map { it.module }.distinct().sorted()
}

/** The Timeline screen: one page of 14 days, then "Show earlier"; a chip filters by module. */
class TimelineViewModel(private val api: TimelineApi) : ViewModel() {
    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private var job: Job? = null
    private var shown = false

    init {
        reload()
    }

    fun refresh() = reload(refreshing = true)

    /** Back on the screen: the first page again (something can have changed in any module). */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(refreshing = false, quiet = true)
    }

    fun pick(module: String?) {
        if (module == _state.value.module) return
        _state.update { it.copy(module = module, pages = emptyList()) }
        reload()
    }

    private fun reload(refreshing: Boolean = false, quiet: Boolean = false) {
        job?.cancel()
        _state.update { it.copy(loading = !quiet, refreshing = refreshing, error = null, moreFailed = false) }
        val module = _state.value.module
        job = viewModelScope.launch {
            val result = apiCall { api.page(days = TIMELINE_PAGE_DAYS, module = module) }
            _state.update { state ->
                val page = result.getOrNull()
                when {
                    page != null -> state.copy(pages = listOf(page), loading = false, refreshing = false)
                    quiet && state.pages.isNotEmpty() -> state.copy(loading = false, refreshing = false)
                    else -> state.copy(
                        pages = emptyList(),
                        loading = false,
                        refreshing = false,
                        error = result.apiError?.loadMessage("Could not load the timeline.") ?: "Could not load the timeline.",
                    )
                }
            }
        }
    }

    /** The 14 days before the last page. */
    fun showEarlier() {
        val state = _state.value
        val before = state.pages.lastOrNull()?.earlier ?: return
        if (state.loading) return
        _state.update { it.copy(loading = true, moreFailed = false) }
        job = viewModelScope.launch {
            val result = apiCall { api.page(before = before, days = TIMELINE_PAGE_DAYS, module = state.module) }
            _state.update {
                val page = result.getOrNull()
                if (page != null) it.copy(pages = it.pages + page, loading = false) else it.copy(loading = false, moreFailed = true)
            }
        }
    }
}
