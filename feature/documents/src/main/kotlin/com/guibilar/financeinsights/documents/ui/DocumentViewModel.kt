package com.guibilar.financeinsights.documents.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentDetails
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.logic.TransactionLookup
import com.guibilar.financeinsights.core.logic.findTransaction
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** A linked transaction, as the detail shows it. */
sealed interface LinkedTransaction {
    data object Loading : LinkedTransaction

    /** Found; [deleted] when it is in the deleted transactions. */
    data class Found(val transaction: Transaction, val deleted: Boolean = false) : LinkedTransaction

    /** Finance does not know it any more. */
    data object Missing : LinkedTransaction

    /** Finance could not be asked. */
    data object Failed : LinkedTransaction
}

data class DocumentState(
    val document: LoadState<Document> = LoadState.Loading,
    /** The linked transactions, by transaction id. */
    val transactions: Map<String, LinkedTransaction> = emptyMap(),
    /** The OCR is still Pending after the polling time: the detail says to come back later. */
    val ocrSlow: Boolean = false,
    /** The document was deleted: the screen goes back. */
    val deleted: Boolean = false,
    /** A write is under way (a link, the OCR retry, the delete): its buttons wait. */
    val busy: Boolean = false,
    /** The transactions to pick a link from; null until the picker asks. */
    val candidates: LoadState<List<Transaction>>? = null,
)

/**
 * One document: its details (edited with a full PUT), its files, its OCR text, and its links to transactions. While
 * the OCR is Pending, it reads the document again every [pollEveryMs], for at most [pollForMs]. [notice] shows once.
 */
class DocumentViewModel(
    private val api: DocumentsApi,
    private val finance: FinanceApi,
    private val id: String,
    private val pollEveryMs: Long = POLL_EVERY_MS,
    private val pollForMs: Long = POLL_FOR_MS,
    /** A message to show once when the page opens, for example a link that failed after the upload. */
    notice: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(DocumentState())
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var pollJob: Job? = null
    private var candidatesJob: Job? = null

    init {
        notice?.let { _messages.trySend(UiMessage(it)) }
        reload()
    }

    val document: Document? get() = _state.value.document.valueOrNull

    fun reload() {
        viewModelScope.launch {
            val loaded = load(LOAD_FAILED) { api.document(id) }
            // A failed reload keeps the document on screen.
            if (loaded is LoadState.Failed && document != null) {
                _messages.trySend(UiMessage(loaded.message))
                return@launch
            }
            show(loaded)
        }
    }

    private fun show(loaded: LoadState<Document>) {
        _state.update { it.copy(document = loaded) }
        val document = loaded.valueOrNull ?: return
        loadTransactions(document)
        if (document.ocrStatus == OcrStatus.Pending) poll() else pollJob?.cancel()
    }

    /** Reads the document again until the OCR is done or failed; the detail then shows its text. */
    private fun poll() {
        if (pollJob?.isActive == true) return
        _state.update { it.copy(ocrSlow = false) }
        pollJob = viewModelScope.launch {
            var waited = 0L
            while (waited < pollForMs) {
                delay(pollEveryMs)
                waited += pollEveryMs
                val read = apiCall { api.document(id) }.getOrNull() ?: continue
                if (read.ocrStatus != OcrStatus.Pending) {
                    _state.update { it.copy(document = LoadState.Loaded(read)) }
                    return@launch
                }
            }
            _state.update { it.copy(ocrSlow = true) }
        }
    }

    /** Asks finance for each linked transaction that is not known yet. */
    private fun loadTransactions(document: Document) {
        val known = _state.value.transactions
        val ids = document.transactionLinks.map { it.targetId }.filter { known[it] !is LinkedTransaction.Found }
        if (ids.isEmpty()) return
        _state.update { state -> state.copy(transactions = state.transactions + ids.associateWith { LinkedTransaction.Loading }) }
        ids.forEach { transactionId ->
            viewModelScope.launch {
                val found = lookUp(transactionId)
                _state.update { state -> state.copy(transactions = state.transactions + (transactionId to found)) }
            }
        }
    }

    /** One transaction by its id (Hestia D13), with the look-up that the Transactions page uses too. */
    private suspend fun lookUp(transactionId: String): LinkedTransaction =
        when (val found = finance.findTransaction(transactionId)) {
            is TransactionLookup.Found -> LinkedTransaction.Found(found.transaction)
            is TransactionLookup.Deleted -> LinkedTransaction.Found(found.transaction, deleted = true)
            TransactionLookup.Missing -> LinkedTransaction.Missing
            TransactionLookup.Failed -> LinkedTransaction.Failed
        }

    /** Saves new details; null when done, or why not. */
    suspend fun save(details: DocumentDetails): String? {
        val result = apiCall { api.update(id, details) }
        result.apiError?.let { error -> return error.validationOr(error.loadMessage("Could not save the document.")) }
        _state.update { it.copy(document = LoadState.Loaded(result.getOrThrow())) }
        DataChanges.changed()
        _messages.trySend(UiMessage("Saved"))
        return null
    }

    fun retryOcr() = write("Could not start the OCR again.") {
        val document = api.retryOcr(id)
        show(LoadState.Loaded(document))
        "Reading the text again"
    }

    fun delete() = write("Could not delete the document.") {
        api.delete(id)
        DataChanges.changed()
        _state.update { it.copy(deleted = true) }
        null
    }

    fun link(transaction: Transaction) = write("Could not link the transaction.") {
        val link = api.addLink(id, NewDocumentLink.transaction(transaction.id))
        _state.update { state ->
            val document = state.document.valueOrNull ?: return@update state
            state.copy(
                document = LoadState.Loaded(document.copy(links = document.links + link)),
                transactions = state.transactions + (transaction.id to LinkedTransaction.Found(transaction)),
            )
        }
        DataChanges.changed()
        "Linked to ${transaction.description}"
    }

    /** Links the document to an item of the home inventory (T88, kind `Home.Item`). */
    fun linkItem(itemId: String, name: String) = write("Could not link the item.") {
        val link = api.addLink(id, NewDocumentLink.item(itemId))
        _state.update { state ->
            val document = state.document.valueOrNull ?: return@update state
            state.copy(document = LoadState.Loaded(document.copy(links = document.links + link)))
        }
        DataChanges.changed()
        "Linked to $name"
    }

    fun unlink(link: DocumentLink) = write("Could not remove the link.") {
        api.removeLink(id, link.id)
        _state.update { state ->
            val document = state.document.valueOrNull ?: return@update state
            state.copy(document = LoadState.Loaded(document.copy(links = document.links.filterNot { it.id == link.id })))
        }
        DataChanges.changed()
        "Link removed"
    }

    /** The transactions to pick a link from: the latest ones, or those whose description has [search]. */
    fun findCandidates(search: String = "") {
        candidatesJob?.cancel()
        _state.update { it.copy(candidates = LoadState.Loading) }
        candidatesJob = viewModelScope.launch {
            if (search.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
            val filter = TransactionFilter(
                description = search.trim().ifEmpty { null },
                sortBy = TransactionSortField.Date,
                sortDirection = SortDirection.Descending,
                page = 1,
                pageSize = CANDIDATES,
            )
            val loaded = load("Could not load the transactions.") { finance.transactions(filter).items }
            _state.update { it.copy(candidates = loaded) }
        }
    }

    fun closeCandidates() {
        candidatesJob?.cancel()
        _state.update { it.copy(candidates = null) }
    }

    /** Runs a write; [action] answers what to tell the person (null for nothing). */
    private fun write(failure: String, action: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { action() }
            _state.update { it.copy(busy = false) }
            val error = result.apiError
            val message = if (error != null) {
                if (error is ApiError.Http && error.status == 403) {
                    "You can only read documents."
                } else {
                    error.validationOr(error.loadMessage(failure))
                }
            } else {
                result.getOrNull()
            }
            message?.let { _messages.trySend(UiMessage(it)) }
        }
    }

    /** Downloads a file into the cache, to open it; the failure says why. */
    suspend fun download(context: Context, file: DocumentFile): Result<File> = apiCall { downloadFile(context, api, id, file) }

    companion object {
        const val POLL_EVERY_MS = 3_000L
        const val POLL_FOR_MS = 120_000L
        const val CANDIDATES = 20
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOAD_FAILED = "Could not load the document."
    }
}
