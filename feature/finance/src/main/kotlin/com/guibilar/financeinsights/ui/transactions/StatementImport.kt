package com.guibilar.financeinsights.ui.transactions

import com.guibilar.financeinsights.core.api.MAX_STATEMENT_BYTES
import com.guibilar.financeinsights.core.logic.importSummary
import com.guibilar.financeinsights.core.model.ImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A statement file picked on the phone: its name and contents. */
class PickedFile(val name: String, val bytes: ByteArray)

/** One of the files picked, and how its import went. */
data class FileImport(val file: PickedFile, val state: FileState = FileState.Waiting, val message: String? = null, val result: ImportResult? = null)

enum class FileState { Waiting, Importing, Imported, Failed, Mismatch }

/** The import sheet: whether it is open, the files and the account they go into, and why some were left out. */
data class ImportState(
    val open: Boolean = false,
    val files: List<FileImport> = emptyList(),
    /** The account picked; null until one is (the sheet picks the only one there is). */
    val accountId: String? = null,
    val error: String? = null,
    /** Every file is in: the sheet closes. */
    val allImported: Boolean = false,
) {
    val uploading get() = files.any { it.state == FileState.Importing }
    val pending get() = files.filter { it.state == FileState.Waiting || it.state == FileState.Failed }
}

/**
 * Importing statements, part of [TransactionsViewModel]: the files and their uploads live as long as it does, so
 * rotating the phone or closing the sheet midway doesn't lose them or stop the uploads.
 */
internal class StatementImport(
    private val scope: CoroutineScope,
    private val send: suspend (file: PickedFile, accountId: String, confirmed: Boolean) -> ImportOutcome,
    /** Every file picked is in, with the sheet open or not. */
    private val imported: (List<ImportResult>) -> Unit,
    /** Some are in and others aren't, with the sheet still open to show which. */
    private val partlyImported: () -> Unit,
    /** The sheet was closed before the files were done: what came of them. */
    private val say: (String) -> Unit,
) {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /**
     * Opens the sheet; with [files] (shared to the app) ready to go. Files already uploading carry on. [accountId] (from
     * a "Statement to import" link, T63) picks that account, unless files are uploading to another one. An id that
     * is not among the accounts picks nothing: the sheet then falls back as before.
     */
    fun open(files: List<PickedFile> = emptyList(), accountId: String? = null) {
        val current = _state.value
        if (current.uploading) {
            _state.update { it.copy(open = true) }
            if (files.isNotEmpty()) take(files)
        } else {
            _state.value = ImportState(open = true, accountId = accountId ?: current.accountId)
            take(files)
        }
    }

    /** The sheet was closed. Uploads under way carry on and say how they went once done. */
    fun close() {
        _state.update { if (it.uploading) it.copy(open = false) else ImportState(accountId = it.accountId) }
    }

    /**
     * Files picked or shared go through the same checks: one over the limit was read only that far, and is left out.
     * While others upload, they join them; otherwise they replace the ones before.
     */
    fun take(picked: List<PickedFile>, unreadable: Int = 0) {
        val tooLarge = picked.filter { it.bytes.size > MAX_STATEMENT_BYTES }
        val error = listOfNotNull(
            when {
                unreadable == 0 -> null
                picked.isEmpty() && unreadable == 1 -> "Could not read that file."
                else -> "Could not read $unreadable of the files."
            },
            when {
                tooLarge.isEmpty() -> null
                tooLarge.size == 1 && picked.size == 1 -> "The file is larger than 5 MB."
                else -> "Left out, larger than 5 MB: ${tooLarge.joinToString { it.name }}."
            },
        ).joinToString(" ").ifEmpty { null }
        val taken = picked.filter { it.bytes.size <= MAX_STATEMENT_BYTES }.map { FileImport(it) }
        _state.update { it.copy(files = if (it.uploading) it.files + taken else taken, error = error, allImported = false) }
    }

    fun chooseAccount(id: String) {
        // The warnings were about the previous choice.
        _state.update { state ->
            state.copy(accountId = id, files = state.files.map { if (it.state == FileState.Mismatch) FileImport(it.file) else it })
        }
    }

    /** Uploads the files waiting (or that failed), one after another, into [accountId]. */
    fun importPending(accountId: String) {
        val current = _state.value
        val toSend = current.pending.map { it.file }
        if (toSend.isEmpty() || current.uploading) return
        _state.update { it.copy(accountId = accountId) }
        // Started here, so the first file is uploading before this returns: closing the sheet right after keeps them.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (file in toSend) upload(file, accountId, confirmed = false)
            finish()
        }
    }

    /** One that looked like another account's goes ahead. */
    fun importAnyway(file: PickedFile, accountId: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            upload(file, accountId, confirmed = true)
            finish()
        }
    }

    private suspend fun upload(file: PickedFile, accountId: String, confirmed: Boolean) {
        update(file, FileImport(file, FileState.Importing))
        update(
            file,
            when (val outcome = send(file, accountId, confirmed)) {
                is ImportOutcome.Imported -> FileImport(file, FileState.Imported, result = outcome.result)
                is ImportOutcome.Mismatch -> FileImport(file, FileState.Mismatch, outcome.message)
                is ImportOutcome.Failed -> FileImport(file, FileState.Failed, outcome.message)
            },
        )
    }

    private fun update(file: PickedFile, entry: FileImport) {
        _state.update { state -> state.copy(files = state.files.map { if (it.file === file) entry else it }) }
    }

    private fun finish() {
        val state = _state.value
        // Another upload is still going: it finishes them.
        if (state.uploading) return
        val done = state.files.filter { it.state == FileState.Imported }
        val left = state.files - done.toSet()
        when {
            state.files.isEmpty() -> {}
            left.isEmpty() -> {
                imported(done.map { it.result!! })
                _state.update { if (it.open) it.copy(allImported = true) else ImportState(accountId = it.accountId) }
            }
            state.open -> if (done.isNotEmpty()) partlyImported()
            else -> {
                // Closed meanwhile: the files that didn't go in are named, as there is no sheet to show them.
                val notImported = "Not imported: ${left.joinToString { it.file.name }}."
                say(if (done.isEmpty()) notImported else "${importSummary(done.map { it.result!! })}. $notImported")
                _state.update { ImportState(accountId = it.accountId) }
            }
        }
    }
}
