package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.FinanceDocuments
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.load as loadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How many documents "Link a document" lists. */
const val LINK_CANDIDATES = 5

private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * The documents of one transaction (T14), from the Documents API of Hestia.Api: the linked ones, and linking another
 * one from a search. Compose state, so the section and its dialog follow it; the calls are suspend functions.
 */
class TransactionDocumentsModel(private val api: DocumentsApi, val transactionId: String) {
    /** The documents linked to the transaction, newest first. */
    var documents by mutableStateOf<LoadState<List<DocumentSummary>>>(LoadState.Loading)
        private set

    /** The documents to link from: the newest, or the best matches of a search. Null until the dialog asks. */
    var candidates by mutableStateOf<LoadState<List<DocumentSummary>>?>(null)
        private set

    /** A link is being made. */
    var busy by mutableStateOf(false)
        private set

    /** Why the last link failed. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The server refused a write: this user can only read documents. */
    var readOnly by mutableStateOf(false)
        private set

    val linkedIds: Set<String> get() = documents.valueOrNull?.map { it.id }.orEmpty().toSet()

    suspend fun load() {
        val loaded = loadState(LOAD_FAILED) { api.byLink(DocumentLinkKinds.MONEY_TRANSACTION, transactionId) }
        // A failed reload keeps the list on screen.
        if (loaded is LoadState.Failed && documents is LoadState.Loaded) return
        documents = loaded
    }

    /** "Retry" after a failed load. */
    suspend fun retry() {
        documents = LoadState.Loading
        load()
    }

    /** The documents to link from: the newest ones, or the best matches of [query]. */
    suspend fun search(query: String) {
        val q = query.trim().ifEmpty { null }
        candidates = loadState(SEARCH_FAILED) { api.documents(q = q, page = 1, pageSize = LINK_CANDIDATES).items }
    }

    /** Links [document] to the transaction, then loads the list again. True when it is linked. */
    suspend fun link(document: DocumentSummary): Boolean {
        if (busy) return false
        busy = true
        error = null
        val failure = apiCall { api.addLink(document.id, NewDocumentLink.transaction(transactionId)) }.apiError
        busy = false
        val status = (failure as? ApiError.Http)?.status
        if (failure == null || status == 409) {
            // Linked; a 409 means the link exists already (from another device), so the list is out of date.
            load()
            return true
        }
        if (status == 403) {
            readOnly = true
        } else {
            error = failure.validationOr(failure.loadMessage("Could not link “${document.title}”."))
        }
        return false
    }

    /** Removes the link of [document] to the transaction (T65), then loads the list again. Both stay. */
    suspend fun unlink(document: DocumentSummary) {
        val link = document.links.firstOrNull {
            it.kind == DocumentLinkKinds.MONEY_TRANSACTION && it.targetId.equals(transactionId, ignoreCase = true)
        } ?: return
        if (busy) return
        busy = true
        error = null
        val failure = apiCall { api.removeLink(document.id, link.id) }.apiError
        busy = false
        when {
            failure == null || (failure as? ApiError.Http)?.status == 404 -> load()
            (failure as? ApiError.Http)?.status == 403 -> readOnly = true
            else -> error = failure.validationOr(failure.loadMessage("Could not remove the link of “${document.title}”."))
        }
    }

    fun clearError() {
        error = null
    }

    companion object {
        const val LOAD_FAILED = "Could not load the documents."
        const val SEARCH_FAILED = "Could not search the documents."
    }
}

/** "Receipt · 1 file". */
fun documentSubtitle(document: DocumentSummary): String {
    val files = document.files.size
    return "${document.kind.label} · $files ${if (files == 1) "file" else "files"}"
}

/**
 * The documents of the transaction whose sheet is open, for the screen that opens the sheet: made and loaded once for
 * each opened transaction. The sheet only shows it. Before, the section made and loaded its own model, so each time
 * the sheet's content was made again (a phone sent 870 requests in 22 seconds), it loaded the list again.
 */
@Composable
fun rememberTransactionDocuments(transactionId: String?, documents: FinanceDocuments?): TransactionDocumentsModel? {
    val model = remember(transactionId, documents) {
        if (transactionId == null || documents == null) null else TransactionDocumentsModel(documents.api, transactionId)
    }
    LaunchedEffect(model) { model?.load() }
    return model
}

/**
 * The "Documents" section of a transaction's sheet: the linked documents (a tap opens one), "Add document" (the
 * Documents upload, which links the new one) and "Link a document". [FinanceDocuments.canWrite] false hides the two
 * actions; so does a write that the server refused with 403. [model] comes from [rememberTransactionDocuments].
 */
@Composable
fun TransactionDocumentsSection(model: TransactionDocumentsModel, documents: FinanceDocuments, modifier: Modifier = Modifier) {
    val transactionId = model.transactionId
    val scope = rememberCoroutineScope()
    var linking by rememberSaveable(transactionId) { mutableStateOf(false) }
    var unlinking by remember(transactionId) { mutableStateOf<DocumentSummary?>(null) }
    TransactionDocumentsContent(
        documents = model.documents,
        canWrite = documents.canWrite && !model.readOnly,
        readOnly = model.readOnly,
        error = if (linking) null else model.error,
        open = { documents.openDocument(it.id) },
        add = { documents.addDocument(transactionId) },
        link = {
            model.clearError()
            linking = !linking
        },
        retry = { scope.launch { model.retry() } },
        modifier = modifier,
        unlink = { unlinking = it },
    )
    if (linking && !model.readOnly) LinkDocumentPanel(model, onClose = { linking = false }, modifier = modifier)
    unlinking?.let { document ->
        AlertDialog(
            onDismissRequest = { unlinking = null },
            title = { Text("Remove the link?") },
            text = { Text("The transaction and “${document.title}” both stay. Only the link goes.") },
            confirmButton = {
                TextButton(onClick = {
                    unlinking = null
                    scope.launch { model.unlink(document) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Cancel") } },
        )
    }
}

/** What [TransactionDocumentsSection] shows, without the API: for the screenshots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDocumentsContent(
    documents: LoadState<List<DocumentSummary>>,
    canWrite: Boolean,
    open: (DocumentSummary) -> Unit,
    add: () -> Unit,
    link: () -> Unit,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    error: String? = null,
    /** Removes the link of a document (T65); shown with [canWrite]. */
    unlink: (DocumentSummary) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        when (documents) {
            LoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading the documents…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(documents.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = retry) { Text("Retry") }
            }
            is LoadState.Loaded -> if (documents.value.isEmpty()) {
                Text(
                    "No documents yet. Add the receipt, for example.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    documents.value.forEach { document ->
                        DocumentRow(document, onClick = { open(document) }, unlink = if (canWrite) ({ unlink(document) }) else null)
                    }
                }
            }
        }
        if (canWrite) {
            // Each button keeps its whole label; with large text, the second one goes to the next line.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = add) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add document")
                }
                OutlinedButton(onClick = link) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link a document")
                }
            }
        } else if (readOnly) {
            Text("You can only read documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DocumentRow(document: DocumentSummary, onClick: () -> Unit, unlink: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${document.title}, ${documentSubtitle(document)}" }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(document.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(documentSubtitle(document), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unlink != null) {
            IconButton(onClick = unlink) { Icon(Icons.Outlined.LinkOff, "Remove the link of ${document.title}") }
        } else {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * "Link a document": a search of the documents under the section, and a "Link" button on each. It closes when the
 * link is made. Not a dialog: a dialog over the sheet adds a window for no gain (the web does the same).
 */
@Composable
private fun LinkDocumentPanel(model: TransactionDocumentsModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        if (model.candidates != null) delay(SEARCH_DEBOUNCE_MS)
        model.search(query)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search documents") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when (val candidates = model.candidates) {
            null, LoadState.Loading -> Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LoadState.Failed -> Text(candidates.message, color = MaterialTheme.colorScheme.error)
            is LoadState.Loaded -> if (candidates.value.isEmpty()) {
                Text(
                    if (query.isBlank()) "No documents yet." else "No document matches “${query.trim()}”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    if (query.isBlank()) "Recent documents" else "Best matches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidates.value.forEach { document ->
                    val linked = document.id in model.linkedIds
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(document.kind.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            enabled = !linked && !model.busy,
                            onClick = { scope.launch { if (model.link(document)) onClose() } },
                            modifier = Modifier.semantics { contentDescription = "Link ${document.title}" },
                        ) { Text(if (linked) "Linked" else "Link") }
                    }
                }
            }
        }
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close the search") }
    }
}
