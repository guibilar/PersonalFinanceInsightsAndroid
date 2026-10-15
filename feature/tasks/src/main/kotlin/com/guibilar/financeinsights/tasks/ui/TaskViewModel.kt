package com.guibilar.financeinsights.tasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.logic.TransactionLookup
import com.guibilar.financeinsights.core.logic.findTransaction
import com.guibilar.financeinsights.core.model.SortDirection
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionSortField
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.people.PersonSummary
import com.guibilar.financeinsights.core.tasks.NewTaskLink
import com.guibilar.financeinsights.core.tasks.TaskDraft
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TasksApi
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

/** A linked thing (a document or a transaction), as the task sheet shows it. */
sealed interface Linked<out T> {
    data object Loading : Linked<Nothing>

    /** Found; [deleted] for a transaction in the deleted ones. */
    data class Found<T>(val value: T, val deleted: Boolean = false) : Linked<T>

    /** Its module does not know it any more. */
    data object Missing : Linked<Nothing>

    /** Its module could not be asked. */
    data object Failed : Linked<Nothing>
}

/** What a link picker lists. */
enum class LinkPicker { Document, Transaction, Item, Person }

data class TaskState(
    val task: LoadState<TaskItem> = LoadState.Loading,
    /** The linked documents, by document id. */
    val documents: Map<String, Linked<DocumentSummary>> = emptyMap(),
    /** The linked transactions, by transaction id. */
    val transactions: Map<String, Linked<Transaction>> = emptyMap(),
    /** A write is under way: its buttons wait. */
    val busy: Boolean = false,
    /** The task was deleted: the sheet closes. */
    val deleted: Boolean = false,
    /** The open link picker; null for none. */
    val picker: LinkPicker? = null,
    val documentCandidates: LoadState<List<DocumentSummary>> = LoadState.Loading,
    val transactionCandidates: LoadState<List<Transaction>> = LoadState.Loading,
    /** The items of the home that the task belongs to (both sides of the link); null without the Home module. */
    val items: LoadState<List<ItemSummary>>? = null,
    val itemCandidates: LoadState<List<ItemSummary>> = LoadState.Loading,
    /** The people that the task is about (both sides of the link, T93); null without the People module. */
    val people: LoadState<List<PersonSummary>>? = null,
    val personCandidates: LoadState<List<PersonSummary>> = LoadState.Loading,
)

/**
 * One task, in the task sheet: its details (saved with a full PUT), done and undo, its links (a document or a
 * transaction, each read from its module; the items of the home through [home], T88), its last completions, and
 * delete. [onChanged] runs after each write, so the Tasks screen shows the change.
 */
class TaskViewModel(
    private val api: TasksApi,
    private val documents: DocumentsApi,
    private val finance: FinanceApi,
    private val id: String,
    private val onChanged: () -> Unit = {},
    /** The Home API, for the items of the task; null leaves them out. */
    private val home: HomeApi? = null,
    /** The People API, for the people of the task (T93); null leaves them out. */
    private val people: PeopleApi? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        TaskState(items = if (home != null) LoadState.Loading else null, people = if (people != null) LoadState.Loading else null),
    )
    val state: StateFlow<TaskState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var pickerJob: Job? = null
    private var shown = false

    init {
        reload()
    }

    /** The sheet opened (again): the task again, as another screen can have changed it. */
    fun opened() {
        if (!shown) {
            shown = true
            return
        }
        closePicker()
        reload()
    }

    val task: TaskItem? get() = _state.value.task.valueOrNull

    fun reload() {
        viewModelScope.launch {
            val loaded = load(LOAD_FAILED) { api.task(id) }
            // A failed reload keeps the task on screen.
            if (loaded is LoadState.Failed && task != null) {
                _messages.trySend(UiMessage(loaded.message))
                return@launch
            }
            show(loaded)
        }
    }

    private fun show(loaded: LoadState<TaskItem>) {
        _state.update { it.copy(task = loaded) }
        loaded.valueOrNull?.let(::loadLinks)
        if (loaded is LoadState.Loaded) {
            loadItems()
            loadPeople()
        }
    }

    /** The people of the task, from the People module (both sides of the link). A failed reload keeps the list. */
    private fun loadPeople() {
        val people = people ?: return
        viewModelScope.launch {
            val loaded = load("Could not load the people.") { people.byLink(PersonLinkKinds.TASK, id) }
            _state.update { s -> if (loaded is LoadState.Failed && s.people is LoadState.Loaded) s else s.copy(people = loaded) }
        }
    }

    /** The items of the task, from the Home module (both sides of the link). A failed reload keeps the list. */
    private fun loadItems() {
        val home = home ?: return
        viewModelScope.launch {
            val loaded = load("Could not load the items.") { home.byLink(ItemLinkKinds.TASK, id) }
            _state.update { s -> if (loaded is LoadState.Failed && s.items is LoadState.Loaded) s else s.copy(items = loaded) }
        }
    }

    /** Asks the modules for each linked thing that is not known yet. */
    private fun loadLinks(task: TaskItem) {
        val state = _state.value
        val documentIds = task.links.filter { it.kind == TaskLinkKinds.DOCUMENT }.map { it.targetId }
            .filter { state.documents[it] !is Linked.Found }
        val transactionIds = task.links.filter { it.kind == TaskLinkKinds.TRANSACTION }.map { it.targetId }
            .filter { state.transactions[it] !is Linked.Found }
        _state.update { s ->
            s.copy(
                documents = s.documents + documentIds.associateWith { Linked.Loading },
                transactions = s.transactions + transactionIds.associateWith { Linked.Loading },
            )
        }
        documentIds.forEach { documentId ->
            viewModelScope.launch {
                val result = apiCall { documents.document(documentId).summary }
                val error = result.apiError
                val found: Linked<DocumentSummary> = when {
                    error == null -> Linked.Found(result.getOrThrow())
                    error is ApiError.Http && error.status == 404 -> Linked.Missing
                    else -> Linked.Failed
                }
                _state.update { s -> s.copy(documents = s.documents + (documentId to found)) }
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

    /** Saves new details; null when done, or why not. */
    suspend fun save(draft: TaskDraft): String? {
        val result = apiCall { api.update(id, draft.toDetails()) }
        result.apiError?.let { error -> return writeError(error, "Could not save the task.") }
        show(LoadState.Loaded(result.getOrThrow()))
        changed()
        _messages.trySend(UiMessage("Saved"))
        return null
    }

    fun done() = write("Could not mark the task done.") {
        show(LoadState.Loaded(api.done(id)))
        "Done"
    }

    fun undo() = write("Could not undo.") {
        show(LoadState.Loaded(api.undo(id)))
        "The last done is undone"
    }

    fun delete() = write("Could not delete the task.") {
        api.delete(id)
        _state.update { it.copy(deleted = true) }
        null
    }

    fun linkDocument(document: DocumentSummary) = write("Could not link the document.") {
        val link = api.addLink(id, NewTaskLink.document(document.id))
        added(link)
        _state.update { s -> s.copy(documents = s.documents + (document.id to Linked.Found(document))) }
        "Linked to ${document.title}"
    }

    fun linkTransaction(transaction: Transaction) = write("Could not link the transaction.") {
        val link = api.addLink(id, NewTaskLink.transaction(transaction.id))
        added(link)
        _state.update { s -> s.copy(transactions = s.transactions + (transaction.id to Linked.Found(transaction))) }
        "Linked to ${transaction.description}"
    }

    /** Links the task to an item of the home (kind `Home.Item`): maintenance. */
    fun linkItem(item: ItemSummary) = write("Could not link the item.") {
        val link = api.addLink(id, NewTaskLink.item(item.id))
        added(link)
        loadItems()
        "Linked to ${item.name}"
    }

    /** Links the task to a person (kind `People.Person`, T93), for example "Call Marco". */
    fun linkPerson(person: PersonSummary) = write("Could not link the person.") {
        val link = api.addLink(id, NewTaskLink.person(person.id))
        added(link)
        loadPeople()
        "Linked to ${person.name}"
    }

    private fun added(link: TaskLink) = _state.update { state ->
        val task = state.task.valueOrNull ?: return@update state
        state.copy(task = LoadState.Loaded(task.copy(links = task.links + link)))
    }

    fun unlink(link: TaskLink) = write("Could not remove the link.") {
        api.removeLink(id, link.id)
        _state.update { state ->
            val task = state.task.valueOrNull ?: return@update state
            state.copy(task = LoadState.Loaded(task.copy(links = task.links.filterNot { it.id == link.id })))
        }
        if (link.kind == TaskLinkKinds.HOME_ITEM) loadItems()
        if (link.kind == TaskLinkKinds.PEOPLE_PERSON) loadPeople()
        "Link removed"
    }

    /** Opens a link picker with the latest documents or transactions. */
    fun openPicker(picker: LinkPicker) {
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
                LinkPicker.Document -> {
                    _state.update { it.copy(documentCandidates = LoadState.Loading) }
                    val loaded = load("Could not load the documents.") { documents.documents(q = query, page = 1, pageSize = CANDIDATES).items }
                    _state.update { it.copy(documentCandidates = loaded) }
                }
                LinkPicker.Transaction -> {
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
                LinkPicker.Person -> {
                    val people = people ?: return@launch
                    _state.update { it.copy(personCandidates = LoadState.Loading) }
                    val loaded = load("Could not load the people.") { people.people(q = query, page = 1, pageSize = CANDIDATES).items }
                    _state.update { it.copy(personCandidates = loaded) }
                }
                LinkPicker.Item -> {
                    val home = home ?: return@launch
                    _state.update { it.copy(itemCandidates = LoadState.Loading) }
                    val loaded = load("Could not load the items.") { home.items(q = query, page = 1, pageSize = CANDIDATES).items }
                    _state.update { it.copy(itemCandidates = loaded) }
                }
            }
        }
    }

    private fun changed() {
        DataChanges.changed()
        onChanged()
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
                changed()
                result.getOrNull()?.let { _messages.trySend(UiMessage(it)) }
            }
        }
    }

    companion object {
        /** How many documents or transactions a picker lists. */
        const val CANDIDATES = 20
        const val SEARCH_DEBOUNCE_MS = 350L
        private const val LOAD_FAILED = "Could not load the task."
    }
}
