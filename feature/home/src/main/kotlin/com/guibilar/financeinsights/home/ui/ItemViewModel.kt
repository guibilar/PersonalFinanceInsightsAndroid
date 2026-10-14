package com.guibilar.financeinsights.home.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.DocumentUpload
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.Item
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.ItemLink
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.MAX_ITEM_NAME
import com.guibilar.financeinsights.core.home.NewItemLink
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.logic.TransactionLookup
import com.guibilar.financeinsights.core.logic.findTransaction
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.core.tasks.MAX_EVERY
import com.guibilar.financeinsights.core.tasks.MAX_TITLE
import com.guibilar.financeinsights.core.tasks.NewTask
import com.guibilar.financeinsights.core.tasks.NewTaskLink
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskRepeat
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A linked thing (a transaction), as the item page shows it. */
sealed interface Linked<out T> {
    data object Loading : Linked<Nothing>

    /** Found; [deleted] for a transaction in the deleted ones. */
    data class Found<T>(val value: T, val deleted: Boolean = false) : Linked<T>

    /** Its module does not know it any more. */
    data object Missing : Linked<Nothing>

    /** Its module could not be asked. */
    data object Failed : Linked<Nothing>
}

/** The picture of a linked document. */
sealed interface Photo {
    data object Loading : Photo

    /** The document's first image, ready to show. */
    data class Image(val bitmap: Bitmap) : Photo

    /** A PDF, or an image that could not be read: an icon shows. */
    data object None : Photo
}

/** What a link picker lists. */
enum class ItemPicker { Document, Transaction }

/** What "Add receipt or photo" adds: a photo of the item, or of its receipt. */
enum class PhotoKind(val kind: DocumentKind, val title: String) {
    ItemPhoto(DocumentKind.Other, "photo"),
    Receipt(DocumentKind.Receipt, "receipt"),
}

data class ItemState(
    val item: LoadState<Item> = LoadState.Loading,
    /** The rooms, for the edit form. */
    val rooms: List<Room> = emptyList(),
    /** The pictures of the linked documents, by document id. */
    val photos: Map<String, Photo> = emptyMap(),
    /** The linked transactions, by transaction id. */
    val transactions: Map<String, Linked<Transaction>> = emptyMap(),
    /** A write is under way: its buttons wait. */
    val busy: Boolean = false,
    /** A photo is being read or uploaded. */
    val uploading: Boolean = false,
    /** The item was deleted: the page closes. */
    val deleted: Boolean = false,
    /** The open link picker; null for none. */
    val picker: ItemPicker? = null,
    val documentCandidates: LoadState<List<DocumentSummary>> = LoadState.Loading,
    val transactionCandidates: LoadState<List<Transaction>> = LoadState.Loading,
) {
    /** The first photo of the item, for the top of the page: the id of its document and the picture. */
    val cover: Pair<String, Bitmap>? get() {
        val item = item.valueOrNull ?: return null
        return item.documents.firstNotNullOfOrNull { document -> (photos[document.id] as? Photo.Image)?.let { document.id to it.bitmap } }
    }
}

/**
 * One item: its details (saved with a full PUT), its documents (with their photos), its purchase, its maintenance
 * tasks, the links, new photos and maintenance, and delete. [decode] turns an image file into a picture (the app does it
 * as Documents does). After each write, Today loads again from the server ([DataChanges]).
 */
class ItemViewModel(
    private val api: HomeApi,
    private val documents: DocumentsApi,
    private val finance: FinanceApi,
    private val tasks: TasksApi,
    val id: String,
    private val decode: (ByteArray, Int) -> Bitmap?,
    notice: String? = null,
    /** Where the pictures are decoded: off the main thread in the app. */
    private val decoding: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ItemState())
    val state: StateFlow<ItemState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var pickerJob: Job? = null
    private var shown = false

    val item: Item? get() = _state.value.item.valueOrNull

    init {
        notice?.let { _messages.trySend(UiMessage(it)) }
        reload()
        viewModelScope.launch {
            apiCall { api.rooms() }.getOrNull()?.let { rooms -> _state.update { it.copy(rooms = rooms) } }
        }
    }

    /** Back on the page (after a document or a task): the item again, as another page can have changed it. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val loaded = load(LOAD_FAILED) { api.item(id) }
            // A failed reload keeps the item on screen.
            if (loaded is LoadState.Failed && item != null) {
                _messages.trySend(UiMessage(loaded.message))
                return@launch
            }
            show(loaded)
        }
    }

    private fun show(loaded: LoadState<Item>) {
        _state.update { it.copy(item = loaded) }
        loaded.valueOrNull?.let(::loadLinked)
    }

    /** Asks the modules for each linked document's picture and each linked transaction that is not known yet. */
    private fun loadLinked(item: Item) {
        val state = _state.value
        val documentIds = item.documents.map { it.id }.filter { it !in state.photos || state.photos[it] == Photo.None }
        val transactionIds = item.links.filter { it.kind == ItemLinkKinds.TRANSACTION }.map { it.targetId }
            .filter { state.transactions[it] !is Linked.Found }
        _state.update { s ->
            s.copy(
                photos = s.photos + documentIds.filter { it !in s.photos }.associateWith { Photo.Loading },
                transactions = s.transactions + transactionIds.associateWith { Linked.Loading },
            )
        }
        documentIds.forEach { documentId ->
            viewModelScope.launch {
                val photo = picture(documentId)
                _state.update { s -> s.copy(photos = s.photos + (documentId to photo)) }
            }
        }
        transactionIds.forEach { transactionId ->
            viewModelScope.launch {
                val found: Linked<Transaction> = when (val lookup = finance.findTransaction(transactionId)) {
                    is TransactionLookup.Found -> Linked.Found(lookup.transaction)
                    is TransactionLookup.Deleted -> Linked.Found(lookup.transaction, deleted = true)
                    TransactionLookup.Missing -> Linked.Missing
                    TransactionLookup.Failed -> Linked.Failed
                }
                _state.update { s -> s.copy(transactions = s.transactions + (transactionId to found)) }
            }
        }
    }

    /** The first image of a document, as a picture; [Photo.None] for a PDF or a file that can't be read. */
    private suspend fun picture(documentId: String): Photo {
        val file = apiCall { documents.document(documentId) }.getOrNull()?.files?.firstOrNull { it.isImage } ?: return Photo.None
        val bytes = apiCall { documents.file(documentId, file.id).use { it.bytes() } }.getOrNull() ?: return Photo.None
        val bitmap = withContext(decoding) { runCatching { decode(bytes, PHOTO_SIDE) }.getOrNull() }
        return bitmap?.let(Photo::Image) ?: Photo.None
    }

    /** Saves new details; null when done, or why not. */
    suspend fun save(draft: ItemDraft): String? {
        val result = apiCall { api.update(id, draft.toDetails()) }
        result.apiError?.let { error -> return writeError(error, "Could not save the item.") }
        show(LoadState.Loaded(result.getOrThrow()))
        DataChanges.changed()
        _messages.trySend(UiMessage("Saved"))
        return null
    }

    fun delete() = write("Could not delete the item.") {
        api.delete(id)
        _state.update { it.copy(deleted = true) }
        null
    }

    fun linkDocument(document: DocumentSummary) = write("Could not link the document.") {
        api.addLink(id, NewItemLink.document(document.id))
        reloadNow()
        "Linked to ${document.title}"
    }

    /** Links the purchase. The price and the day of the purchase fill the item's when they are empty. */
    fun linkTransaction(transaction: Transaction) = write("Could not link the purchase.") {
        val link = api.addLink(id, NewItemLink.transaction(transaction.id))
        _state.update { s -> s.copy(transactions = s.transactions + (transaction.id to Linked.Found(transaction))) }
        val current = item
        if (current != null) {
            val draft = ItemDraft.of(current)
            val filled = draft.withPurchase(transaction.amount, transaction.day)
            if (filled != draft) {
                show(LoadState.Loaded(api.update(id, filled.toDetails())))
                return@write "Linked. The price and the day come from the purchase."
            }
            show(LoadState.Loaded(current.copy(links = current.links + link)))
        }
        "Linked to ${transaction.description}"
    }

    fun unlink(link: ItemLink) = write("Could not remove the link.") {
        api.removeLink(id, link.id)
        reloadNow()
        "Link removed"
    }

    /** A photo of the item or its receipt: uploaded to Documents and linked to the item. */
    fun addPhoto(photo: UploadFile?, kind: PhotoKind) {
        if (photo == null) {
            _state.update { it.copy(uploading = false) }
            _messages.trySend(UiMessage("Could not read the photo. Try another one."))
            return
        }
        val name = item?.name ?: "Item"
        write("Could not upload the photo.") {
            _state.update { it.copy(uploading = true) }
            try {
                val upload = DocumentUpload(listOf(photo), title = "$name ${kind.title}".take(MAX_ITEM_NAME), kind = kind.kind)
                val document = documents.upload(upload.toMultipart())
                api.addLink(id, NewItemLink.document(document.id))
                reloadNow()
                "Added “${document.title}”"
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    /** A photo is being read. */
    fun preparing() = _state.update { it.copy(uploading = true) }

    /**
     * A repeated task linked to the item: [title], every [every] [unit] after each done, first due on [dueOn]. Null
     * when done, or why not.
     */
    suspend fun addMaintenance(title: String, every: Int?, unit: RepeatUnit, dueOn: LocalDate): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return "Enter what to do."
        if (clean.length > MAX_TITLE) return "At most $MAX_TITLE characters."
        if (every == null || every !in 1..MAX_EVERY) return "Use 1 to $MAX_EVERY."
        val task = NewTask(
            title = clean,
            dueOn = dueOn.toString(),
            repeat = TaskRepeat(every, unit, RepeatMode.FromCompletion),
            links = listOf(NewTaskLink.item(id)),
        )
        val result = apiCall { tasks.create(task) }
        result.apiError?.let { return writeError(it, "Could not add the maintenance task.") }
        DataChanges.changed()
        _messages.trySend(UiMessage("Added “$clean”"))
        reload()
        return null
    }

    /** Opens a link picker with the latest documents or transactions. */
    fun openPicker(picker: ItemPicker) {
        _state.update { it.copy(picker = picker) }
        search("")
    }

    fun closePicker() {
        pickerJob?.cancel()
        _state.update { it.copy(picker = null) }
    }

    /** The candidates of the open picker: the latest ones, or the ones that match [text]. */
    fun search(text: String) {
        val picker = _state.value.picker ?: return
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            if (text.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
            val query = text.trim().ifEmpty { null }
            when (picker) {
                ItemPicker.Document -> {
                    _state.update { it.copy(documentCandidates = LoadState.Loading) }
                    val loaded = load("Could not load the documents.") { documents.documents(q = query, page = 1, pageSize = CANDIDATES).items }
                    _state.update { it.copy(documentCandidates = loaded) }
                }
                ItemPicker.Transaction -> {
                    _state.update { it.copy(transactionCandidates = LoadState.Loading) }
                    val filter = TransactionFilter(
                        description = query,
                        sortBy = TransactionSortField.Date,
                        sortDirection = SortDirection.Descending,
                        page = 1,
                        pageSize = CANDIDATES,
                    )
                    val loaded = load("Could not load the transactions.") { finance.transactions(filter).items }
                    _state.update { it.copy(transactionCandidates = loaded) }
                }
            }
        }
    }

    private suspend fun reloadNow() {
        val loaded = api.item(id)
        show(LoadState.Loaded(loaded))
    }

    /** Runs a write; [action] answers what to tell the person (null for nothing). */
    private fun write(failure: String, action: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { action() }
            _state.update { it.copy(busy = false) }
            val error = result.apiError
            if (error != null) {
                _messages.trySend(UiMessage(writeError(error, failure)))
                if (error is ApiError.Http && error.status == 409) reload()
            } else {
                DataChanges.changed()
                result.getOrNull()?.let { _messages.trySend(UiMessage(it)) }
            }
        }
    }

    companion object {
        /** How many documents or transactions a picker lists. */
        const val CANDIDATES = 20
        const val SEARCH_DEBOUNCE_MS = 350L

        /** The long side of a photo on the page, in pixels. */
        const val PHOTO_SIDE = 1200
        private const val LOAD_FAILED = "Could not load the item."
    }
}
