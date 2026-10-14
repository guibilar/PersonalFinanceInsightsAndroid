package com.guibilar.financeinsights.documents.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentUpload
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.documents.uploadProblem
import com.guibilar.financeinsights.core.logic.DataChanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UploadState(
    val files: List<UploadFile> = emptyList(),
    val draft: DetailsDraft = DetailsDraft(),
    /** Picked files are being read and made small. */
    val preparing: Boolean = false,
    val uploading: Boolean = false,
    /** Why the files can't go, or why the server refused them. */
    val error: String? = null,
    /** The new document, once the server has it: the screen opens it. */
    val uploaded: Document? = null,
) {
    /** The title when the field is left empty: the server takes the name of the first file. */
    val titleHint get() = files.firstOrNull()?.name
}

/** A new document: the files picked (or scanned), the details, and the upload. */
class UploadViewModel(private val api: DocumentsApi) : ViewModel() {
    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    fun preparing(on: Boolean) = _state.update { it.copy(preparing = on) }

    /** Files picked or scanned join the ones there; [unreadable] could not be read. */
    fun add(files: List<UploadFile>, unreadable: Int = 0) {
        _state.update { state ->
            val all = state.files + files
            state.copy(
                files = all,
                preparing = false,
                error = listOfNotNull(
                    when (unreadable) {
                        0 -> null
                        1 -> "Could not read one of the files."
                        else -> "Could not read $unreadable of the files."
                    },
                    uploadProblem(all).takeIf { all.isNotEmpty() },
                ).joinToString(" ").ifEmpty { null },
            )
        }
    }

    fun remove(file: UploadFile) {
        _state.update { state ->
            val left = state.files.filterNot { it === file }
            state.copy(files = left, error = uploadProblem(left).takeIf { left.isNotEmpty() })
        }
    }

    fun edit(draft: DetailsDraft) = _state.update { it.copy(draft = draft) }

    /** Sends the files and the details. The new document comes back in [UploadState.uploaded]. */
    fun upload() {
        val current = _state.value
        if (current.uploading || current.preparing) return
        val problem = uploadProblem(current.files) ?: current.draft.expiryError
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val details = current.draft.toDetails()
            val upload = DocumentUpload(
                files = current.files,
                title = details.title,
                kind = details.kind,
                tags = details.tags,
                issuedOn = details.issuedOn,
                expiresOn = details.expiresOn,
                notes = details.notes,
            )
            val result = apiCall { api.upload(upload.toMultipart()) }
            val error = result.apiError
            if (error == null) DataChanges.changed()
            _state.update { state ->
                if (error == null) {
                    state.copy(uploading = false, uploaded = result.getOrThrow())
                } else {
                    state.copy(uploading = false, error = uploadFailure(error))
                }
            }
        }
    }

    private fun uploadFailure(error: ApiError): String = when {
        error is ApiError.Http && error.status == 413 ->
            "The server refused the upload as too large. Try fewer files, or smaller ones."
        error is ApiError.Http && error.status == 403 -> "You can only read documents."
        else -> error.validationOr(error.loadMessage("Could not upload the document."))
    }
}
