package com.guibilar.financeinsights.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentUpload
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.NewItemLink
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.core.logic.DataChanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewItemState(
    val rooms: List<Room> = emptyList(),
    /** The photo to upload with the item, ready (scaled). */
    val photo: UploadFile? = null,
    /** A photo is being read. */
    val preparing: Boolean = false,
    val saving: Boolean = false,
    /** Why the last save failed. */
    val error: String? = null,
    /** The new item's id, once it is saved: the page then opens it. */
    val created: String? = null,
    /** What to tell on the new item's page: a photo or a link that did not go. */
    val notice: String? = null,
)

/**
 * A new item: the form ([initial]), an optional photo, and the purchase it comes from ([transactionId], "Add to
 * inventory" in Money). Save adds the item with the link to the purchase, then uploads the photo as a document
 * (kind Other) and links it to the item.
 */
class NewItemViewModel(
    private val api: HomeApi,
    private val documents: DocumentsApi,
    val initial: ItemDraft,
    private val transactionId: String? = null,
) : ViewModel() {
    /** The categories in use (T89), for the suggestions of the form; none when they do not load. */
    suspend fun categories(): List<String> = apiCall { api.categories() }.getOrNull()?.map { it.name }.orEmpty()

    private val _state = MutableStateFlow(NewItemState())
    val state: StateFlow<NewItemState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            apiCall { api.rooms() }.getOrNull()?.let { rooms -> _state.update { it.copy(rooms = rooms) } }
        }
    }

    /** A photo is being read (the camera or the picker gave one). */
    fun preparing() = _state.update { it.copy(preparing = true) }

    /** The photo, ready; null when it could not be read. */
    fun photo(photo: UploadFile?) = _state.update {
        it.copy(
            photo = photo ?: it.photo,
            preparing = false,
            error = if (photo == null) "Could not read the photo. Try another one." else null,
        )
    }

    fun removePhoto() = _state.update { it.copy(photo = null) }

    /** Adds the item, then its photo. */
    fun save(draft: ItemDraft) {
        if (!draft.canSave || _state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val links = listOfNotNull(transactionId?.let(NewItemLink::transaction))
            val result = apiCall { api.create(draft.toNewItem(links)) }
            val error = result.apiError
            if (error != null) {
                _state.update { it.copy(saving = false, error = writeError(error, "Could not add the item.")) }
                return@launch
            }
            val item = result.getOrThrow()
            DataChanges.changed()
            val photo = _state.value.photo
            val notice = if (photo != null) uploadPhoto(item.id, item.name, photo) else null
            _state.update { it.copy(saving = false, created = item.id, notice = notice) }
        }
    }

    /** Uploads the photo as a document and links it to the item; what to tell when it did not go, else null. */
    private suspend fun uploadPhoto(itemId: String, name: String, photo: UploadFile): String? {
        val uploaded = apiCall { documents.upload(DocumentUpload(listOf(photo), title = "$name photo", kind = DocumentKind.Other).toMultipart()) }
        val document = uploaded.getOrNull() ?: return "The item was added, but the photo could not be uploaded. Add it again on this page."
        val linked = apiCall { api.addLink(itemId, NewItemLink.document(document.id)) }
        return if (linked.apiError != null) "The photo is in Documents as “${document.title}”, but it could not be linked." else null
    }
}
