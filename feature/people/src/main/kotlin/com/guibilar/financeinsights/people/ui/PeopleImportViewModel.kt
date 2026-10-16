package com.guibilar.financeinsights.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.core.people.PeopleImportRequest
import com.guibilar.financeinsights.core.people.PeopleImportResult
import com.guibilar.financeinsights.core.people.PersonDetails
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleImportState(
    val busy: Boolean = false,
    /** What was sent for the preview, to send again with the choices. */
    val request: PeopleImportRequest? = null,
    val preview: PeopleImportResult? = null,
    val chosen: Set<Int> = emptySet(),
    val error: String? = null,
) {
    val choosable: List<Int> get() = preview?.people.orEmpty().filter { it.choosable }.map { it.index }
    val allChosen: Boolean get() = choosable.isNotEmpty() && chosen.size == choosable.size
}

private fun importError(error: ApiError?, failure: String): String = when {
    error is ApiError.Http && error.status == 403 -> "You can only read people."
    error != null -> error.validationOr(error.loadMessage(failure))
    else -> failure
}

/**
 * Import contacts (T105): the phone's contacts or a vCard file go to the server for a preview (new, update, the same,
 * skipped), then the chosen ones are imported. The server never overwrites what the person wrote.
 */
class PeopleImportViewModel(private val api: PeopleApi) : ViewModel() {
    private val _state = MutableStateFlow(PeopleImportState())
    val state: StateFlow<PeopleImportState> = _state.asStateFlow()

    /** Once the import is done: how many were added and updated. */
    private val _done = Channel<String>(Channel.BUFFERED)
    val done: Flow<String> = _done.receiveAsFlow()

    fun fromPhone(people: List<PersonDetails>) {
        if (people.isEmpty()) {
            _state.update { it.copy(error = "The phone has no contacts with a name.") }
            return
        }
        preview(PeopleImportRequest(people = people))
    }

    fun fromVCard(text: String) = preview(PeopleImportRequest(vCard = text))

    fun failed(message: String) = _state.update { it.copy(error = message, busy = false) }

    private fun preview(request: PeopleImportRequest) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = apiCall { api.importPeople(request.copy(dryRun = true)) }
            result.onSuccess { preview ->
                _state.update {
                    it.copy(busy = false, request = request, preview = preview, chosen = preview.people.filter { p -> p.choosable }.map { p -> p.index }.toSet())
                }
            }.onFailure { _state.update { it.copy(busy = false, error = importError(result.apiError, "Could not read the contacts.")) } }
        }
    }

    fun toggle(index: Int) = _state.update { it.copy(chosen = if (index in it.chosen) it.chosen - index else it.chosen + index) }

    fun toggleAll() = _state.update { it.copy(chosen = if (it.allChosen) emptySet() else it.choosable.toSet()) }

    fun startOver() = _state.update { PeopleImportState() }

    fun confirm() {
        val state = _state.value
        val request = state.request ?: return
        if (state.chosen.isEmpty()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = apiCall { api.importPeople(request.copy(dryRun = false, only = state.chosen.sorted())) }
            result.onSuccess { done ->
                DataChanges.changed()
                _state.update { it.copy(busy = false) }
                val parts = listOfNotNull(
                    done.added.takeIf { it > 0 }?.let { "$it ${if (it == 1) "person" else "people"} added" },
                    done.merged.takeIf { it > 0 }?.let { "$it updated" },
                )
                _done.send(if (parts.isEmpty()) "Nothing to import." else parts.joinToString(", ") + ".")
            }.onFailure { _state.update { it.copy(busy = false, error = importError(result.apiError, "Could not import the contacts.")) } }
        }
    }
}
