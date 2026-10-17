package com.guibilar.financeinsights.documents.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.expireSoonRange
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DocumentsListState(
    /** What is typed in the search field; searched after a pause. */
    val search: String = "",
    /** The search sent to the server; null for none. */
    val query: String? = null,
    /** Null: all kinds. */
    val kind: DocumentKind? = null,
    val items: List<DocumentSummary> = emptyList(),
    val totalCount: Int = 0,
    val pagesLoaded: Int = 0,
    /** The server has a page after the last one loaded. */
    val hasNextPage: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** A later page failed: the list stays, with a retry at its end. */
    val moreFailed: Boolean = false,
    val refreshing: Boolean = false,
    /** The documents that expire in the next 30 days, the soonest first. Shown only without a filter. */
    val expiring: List<DocumentSummary> = emptyList(),
) {
    // By the server's pages, not the count: a document added meanwhile repeats one on the next page, which is dropped.
    val endReached get() = pagesLoaded > 0 && !hasNextPage
    val hasCriteria get() = query != null || kind != null
}

/** The documents: searched (full text), filtered by kind, a page at a time; and the ones that expire soon. */
@OptIn(FlowPreview::class)
class DocumentsListViewModel(
    private val api: DocumentsApi,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val thumbnails = DocumentThumbnails(api)

    /** The thumbnail of the first file of [document] (T61), or null: the row shows the icon of its kind. */
    suspend fun thumbnail(document: DocumentSummary): ImageBitmap? =
        document.files.firstOrNull()?.let { thumbnails.load(document.id, it.id) }

    private val _state = MutableStateFlow(DocumentsListState())
    val state: StateFlow<DocumentsListState> = _state.asStateFlow()

    // Every text typed, the same one again included (a StateFlow would drop that).
    private val searchText = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var listJob: Job? = null
    private var shown = false

    init {
        searchText.debounce(SEARCH_DEBOUNCE_MS)
            .onEach { text ->
                val query = text.trim().ifEmpty { null }
                if (query == _state.value.query) return@onEach
                _state.update { it.copy(query = query) }
                reload()
            }
            .launchIn(viewModelScope)
        reload()
    }

    /** Back on the screen (after an upload, an edit or a delete): the list again, kept if that fails. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchText.tryEmit(text)
    }

    fun setKind(kind: DocumentKind?) {
        if (kind == _state.value.kind) return
        _state.update { it.copy(kind = kind) }
        reload()
    }

    fun clearAll() {
        _state.update { it.copy(search = "", query = null, kind = null) }
        searchText.tryEmit("")
        reload()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.error != null) return
        _state.update { it.copy(loadingMore = true, moreFailed = false) }
        listJob = viewModelScope.launch {
            val result = apiCall { page(current, current.pagesLoaded + 1) }
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            items = (state.items + page.items).distinctBy { it.id },
                            totalCount = page.totalCount,
                            pagesLoaded = page.page,
                            hasNextPage = page.hasNextPage,
                            loadingMore = false,
                        )
                    },
                    onFailure = { state.copy(loadingMore = false, moreFailed = true) },
                )
            }
        }
    }

    /** The first page again, and the "Expire soon" section. [quiet] keeps the list showing when the reload fails. */
    private fun reload(quiet: Boolean = false) {
        listJob?.cancel()
        _state.update { it.copy(loading = true, error = null, loadingMore = false, moreFailed = false) }
        listJob = viewModelScope.launch {
            val current = _state.value
            val result = apiCall { page(current, 1) }
            val expiring = if (current.hasCriteria) {
                Result.success(emptyList())
            } else {
                val (from, to) = expireSoonRange(today())
                apiCall { api.documents(expiresFrom = from, expiresTo = to, pageSize = EXPIRING_PAGE_SIZE).items.sortedBy { it.expiresOn } }
            }
            _state.update { state ->
                result.fold(
                    onSuccess = { page ->
                        state.copy(
                            items = page.items,
                            totalCount = page.totalCount,
                            pagesLoaded = 1,
                            hasNextPage = page.hasNextPage,
                            loading = false,
                            refreshing = false,
                            // A failed section keeps what it showed: the list is what matters.
                            expiring = if (current.hasCriteria) emptyList() else expiring.getOrDefault(state.expiring),
                        )
                    },
                    onFailure = {
                        if (quiet && state.items.isNotEmpty()) return@fold state.copy(loading = false, refreshing = false)
                        state.copy(
                            items = emptyList(), pagesLoaded = 0, loading = false, refreshing = false, expiring = emptyList(),
                            error = result.apiError?.loadMessage(LOAD_FAILED) ?: LOAD_FAILED,
                        )
                    },
                )
            }
        }
    }

    private suspend fun page(state: DocumentsListState, page: Int) =
        api.documents(kind = state.kind, q = state.query, page = page, pageSize = PAGE_SIZE)

    companion object {
        const val PAGE_SIZE = 20
        const val EXPIRING_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOAD_FAILED = "Could not load documents."
    }
}
