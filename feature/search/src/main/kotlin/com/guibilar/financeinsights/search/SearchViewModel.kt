package com.guibilar.financeinsights.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The shortest search that the server takes. */
const val MIN_SEARCH = 2

/** The longest search that the server takes. */
const val MAX_SEARCH = 200

data class SearchState(
    /** What is typed. */
    val text: String = "",
    /** Only this module, with more hits; null for all. */
    val module: String? = null,
    val result: SearchResult? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** The text as the server takes it: trimmed, with single spaces. */
    val query: String get() = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    val tooShort: Boolean get() = query.length < MIN_SEARCH

    /** The modules with hits. */
    val found: List<SearchModule> get() = result?.modules.orEmpty().filter { it.hits.isNotEmpty() }

    val failed: List<String> get() = result?.modules.orEmpty().filter { it.status == "Failed" }.map { it.module }
}

/** Search: one search across all modules, after a pause in the typing. */
class SearchViewModel(private val api: SearchApi) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun type(text: String) {
        _state.update { it.copy(text = text.take(MAX_SEARCH)) }
        search(delayMillis = 300)
    }

    /** All hits of one module ("Show all"), or all modules again (null). */
    fun only(module: String?) {
        _state.update { it.copy(module = module) }
        search(delayMillis = 0)
    }

    fun retry() = search(delayMillis = 0)

    private fun search(delayMillis: Long) {
        job?.cancel()
        val state = _state.value
        if (state.tooShort) {
            _state.update { it.copy(result = null, loading = false, error = null) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            delay(delayMillis)
            val result = apiCall { api.search(state.query, state.module) }
            _state.update {
                val found = result.getOrNull()
                if (found != null) {
                    it.copy(result = found, loading = false)
                } else {
                    // The old hits were for another text: the error takes their place.
                    it.copy(result = null, loading = false, error = result.apiError?.loadMessage("Could not search.") ?: "Could not search.")
                }
            }
        }
    }
}
