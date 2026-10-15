package com.guibilar.financeinsights.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.people.GiftDetails
import com.guibilar.financeinsights.core.people.GiftIdea
import com.guibilar.financeinsights.core.people.GroupCount
import com.guibilar.financeinsights.core.people.Interaction
import com.guibilar.financeinsights.core.people.InteractionDetails
import com.guibilar.financeinsights.core.people.Loan
import com.guibilar.financeinsights.core.people.LoanDetails
import com.guibilar.financeinsights.core.people.LoanDirection
import com.guibilar.financeinsights.core.people.NewPersonLink
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.core.people.PeopleSummary
import com.guibilar.financeinsights.core.people.Person
import com.guibilar.financeinsights.core.people.PersonDraft
import com.guibilar.financeinsights.core.people.PersonLink
import com.guibilar.financeinsights.core.people.PersonSummary
import com.guibilar.financeinsights.core.people.given
import com.guibilar.financeinsights.core.people.peopleDay
import com.guibilar.financeinsights.core.people.returned
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What to tell the person when a write failed: a read-only user, the server's reasons, or [failure]. */
internal fun writeError(error: ApiError, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read the people."
    error is ApiError.Http && error.status == 404 -> "This person or record does not exist any more."
    error is ApiError.Http && error.status == 409 -> error.conflictOr(failure)
    else -> error.validationOr(error.loadMessage(failure))
}

data class PeopleState(
    val summary: LoadState<PeopleSummary> = LoadState.Loading,
    val people: LoadState<List<PersonSummary>> = LoadState.Loading,
    val totalCount: Int = 0,
    val groups: List<GroupCount> = emptyList(),
    val loans: List<Loan> = emptyList(),
    /** Today, as the server counts it (the summary). */
    val today: LocalDate = LocalDate.now(),
    val refreshing: Boolean = false,
    val search: String = "",
    /** The group chip that is on, or null for all. */
    val group: String? = null,
)

/**
 * The People screen: the summary (reach out, upcoming birthdays), the open loans, the groups, and all people (a group
 * and a search filter them).
 */
class PeopleViewModel(
    private val api: PeopleApi,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState(today = today()))
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var shown = false

    init {
        reload()
    }

    /** Pull to refresh, or "Try again". */
    fun refresh() = reload(refreshing = true)

    /** Back on the screen (after a person changed): everything again, kept if that fails. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    private fun reload(quiet: Boolean = false, refreshing: Boolean = false) {
        if (!quiet) _state.update { it.copy(refreshing = refreshing) }
        viewModelScope.launch {
            coroutineScope {
                val summary = async { load("Could not load the people.") { api.summary() } }
                val groups = async { runCatching { api.groups() }.getOrNull() }
                val loans = async { runCatching { api.loans() }.getOrNull() }
                val loaded = summary.await()
                _state.update { state ->
                    state.copy(
                        summary = if (loaded is LoadState.Failed && quiet && state.summary is LoadState.Loaded) state.summary else loaded,
                        groups = groups.await() ?: state.groups,
                        loans = loans.await() ?: state.loans,
                        today = peopleDay(loaded.valueOrNull?.date) ?: state.today,
                        refreshing = false,
                    )
                }
            }
            loadPeople(quiet)
        }
    }

    private suspend fun loadPeople(quiet: Boolean) {
        val state = _state.value
        val result = apiCall { api.people(group = state.group, q = state.search.trim().ifEmpty { null }, sort = "name", pageSize = PAGE_SIZE) }
        _state.update { current ->
            result.apiError?.let { error ->
                if (quiet && current.people is LoadState.Loaded) current
                else current.copy(people = LoadState.Failed(error.loadMessage("Could not load the people.")))
            } ?: result.getOrThrow().let { page -> current.copy(people = LoadState.Loaded(page.items), totalCount = page.totalCount) }
        }
    }

    /** A new search text; the list follows once typing pauses. */
    fun search(text: String) {
        _state.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadPeople(quiet = false)
        }
    }

    /** A group chip: on (that group), or off again (all). */
    fun pickGroup(group: String?) {
        _state.update { it.copy(group = group, people = LoadState.Loading) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPeople(quiet = false) }
    }

    companion object {
        /** The most people a page has (the server's limit). */
        const val PAGE_SIZE = 100

        const val SEARCH_DEBOUNCE_MS = 350L
    }
}

/** The quick action whose dialog is open on the person screen. */
enum class PersonAction { Contact, Gift, Loan }

data class PersonState(
    val person: LoadState<Person> = LoadState.Loading,
    val refreshing: Boolean = false,
    /** A write is under way. */
    val busy: Boolean = false,
)

/** One person: the details and records, and the writes on them (contacts, gift ideas, loans, links, delete). */
class PersonViewModel(
    private val api: PeopleApi,
    private val id: String,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state: StateFlow<PersonState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var shown = false

    init {
        reload()
    }

    fun refresh() = reload(refreshing = true)

    /** Back on the screen (after an edit): the person again, kept if that fails. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    private fun reload(quiet: Boolean = false, refreshing: Boolean = false) {
        if (!quiet) _state.update { it.copy(refreshing = refreshing) }
        viewModelScope.launch {
            val loaded = load("Could not load the person.") { api.person(id) }
            _state.update { state ->
                state.copy(
                    person = if (loaded is LoadState.Failed && quiet && state.person is LoadState.Loaded) state.person else loaded,
                    refreshing = false,
                )
            }
        }
    }

    /** One write: busy while it runs; then the person again, Today fresh ([DataChanges]), and [success]. True when done. */
    private suspend fun write(success: String, failure: String, block: suspend () -> Unit): Boolean {
        if (_state.value.busy) return false
        _state.update { it.copy(busy = true) }
        val result = apiCall { block() }
        _state.update { it.copy(busy = false) }
        result.apiError?.let {
            _messages.trySend(UiMessage(writeError(it, failure)))
            return false
        }
        DataChanges.changed()
        _messages.trySend(UiMessage(success))
        reload(quiet = true)
        return true
    }

    /** Logs a contact; [day] null is today. A day in the future is refused here, as the server would. */
    suspend fun logContact(kind: String?, note: String, day: LocalDate?): Boolean {
        if (day != null && day.isAfter(today())) {
            _messages.trySend(UiMessage("A contact cannot be in the future."))
            return false
        }
        return write("Logged the contact", "Could not log the contact.") {
            api.logInteraction(id, InteractionDetails(day?.toString(), kind, note.trim().ifEmpty { null }))
        }
    }

    suspend fun addGift(title: String, price: Double?, occasion: String): Boolean {
        if (title.isBlank()) {
            _messages.trySend(UiMessage("Enter the gift idea."))
            return false
        }
        return write("Added the gift idea", "Could not add the gift idea.") {
            api.addGift(id, GiftDetails(title.trim(), price = price, occasion = occasion.trim().ifEmpty { null }))
        }
    }

    suspend fun addLoan(itemName: String, lent: Boolean, dueOn: LocalDate?): Boolean {
        if (itemName.isBlank()) {
            _messages.trySend(UiMessage("Enter the thing."))
            return false
        }
        val direction = if (lent) LoanDirection.Lent else LoanDirection.Borrowed
        return write(if (lent) "Added the lent item" else "Added the borrowed item", "Could not add the loan.") {
            api.addLoan(id, LoanDetails(itemName.trim(), direction, dueOn = dueOn?.toString()))
        }
    }

    /** The checkbox of a gift idea: given today, or (unchecked) an idea again. */
    fun toggleGiven(gift: GiftIdea) {
        val day = if (gift.isGiven) null else today()
        viewModelScope.launch {
            write(if (day != null) "Marked “${gift.title}” as given" else "“${gift.title}” is an idea again", "Could not change the gift idea.") {
                api.updateGift(gift.id, gift.given(day))
            }
        }
    }

    fun deleteGift(gift: GiftIdea) {
        viewModelScope.launch { write("Deleted “${gift.title}”", "Could not delete the gift idea.") { api.deleteGift(gift.id) } }
    }

    /** "Got it back" or "Gave it back". */
    fun returned(loan: Loan) {
        viewModelScope.launch { write("“${loan.itemName}” is back", "Could not change the loan.") { api.updateLoan(loan.id, loan.returned(today())) } }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch { write("Deleted “${loan.itemName}”", "Could not delete the loan.") { api.deleteLoan(loan.id) } }
    }

    fun deleteContact(contact: Interaction) {
        viewModelScope.launch { write("Deleted the contact", "Could not delete the contact.") { api.deleteInteraction(contact.id) } }
    }

    /** Links the person to a thing picked in "Add a link" (T94). */
    fun addLink(kind: String, targetId: String, name: String) {
        viewModelScope.launch { write("Linked to “$name”", "Could not add the link.") { api.addLink(id, NewPersonLink(kind, targetId)) } }
    }

    fun unlink(link: PersonLink) {
        viewModelScope.launch { write("Removed the link", "Could not remove the link.") { api.removeLink(id, link.id) } }
    }

    /** Deletes the person; true when done (the screen then closes). */
    suspend fun delete(): Boolean {
        if (_state.value.busy) return false
        _state.update { it.copy(busy = true) }
        val result = apiCall { api.delete(id) }
        _state.update { it.copy(busy = false) }
        result.apiError?.let {
            _messages.trySend(UiMessage(writeError(it, "Could not delete the person.")))
            return false
        }
        DataChanges.changed()
        return true
    }
}

data class PersonFormState(
    val draft: PersonDraft = PersonDraft(),
    /** False while the person of a change loads. */
    val ready: Boolean = true,
    val loadError: String? = null,
    /** The reasons by field, after a save that the checks stopped. */
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
)

/** The person form: a new person, or (with [id]) the details of one, replaced in full by a save. */
class PersonFormViewModel(
    private val api: PeopleApi,
    private val id: String?,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(PersonFormState(ready = id == null))
    val state: StateFlow<PersonFormState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val isNew get() = id == null

    init {
        if (id != null) load(id)
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val result = apiCall { api.person(id) }
            _state.update { state ->
                result.apiError?.let { state.copy(loadError = it.loadMessage("Could not load the person.")) }
                    ?: state.copy(draft = PersonDraft.of(result.getOrThrow()), ready = true, loadError = null)
            }
        }
    }

    fun edit(change: (PersonDraft) -> PersonDraft) = _state.update { it.copy(draft = change(it.draft), errors = emptyMap()) }

    /** Saves; the id of the person when done, else null (the errors or the message say why). */
    suspend fun save(): String? {
        val state = _state.value
        if (state.busy || !state.ready) return null
        val errors = state.draft.check(today())
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return null
        }
        _state.update { it.copy(busy = true) }
        val details = state.draft.details()
        val result = apiCall { if (id == null) api.create(details) else api.update(id, details) }
        _state.update { it.copy(busy = false) }
        result.apiError?.let {
            _messages.trySend(UiMessage(writeError(it, if (id == null) "Could not add the person." else "Could not save the person.")))
            return null
        }
        DataChanges.changed()
        return result.getOrThrow().person.id
    }
}
