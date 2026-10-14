package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * The "Documents" section of a transaction's sheet: the linked documents (a tap opens one), "Add document" (the
 * Documents upload, which links the new one) and "Link a document". [FinanceDocuments.canWrite] false hides the two
 * actions; so does a write that the server refused with 403.
 */
@Composable
fun TransactionDocumentsSection(transactionId: String, documents: FinanceDocuments, modifier: Modifier = Modifier) {
    val model = remember(transactionId, documents.api) { TransactionDocumentsModel(documents.api, transactionId) }
    LaunchedEffect(model) { model.load() }
    val scope = rememberCoroutineScope()
    var linking by rememberSaveable(transactionId) { mutableStateOf(false) }
    TransactionDocumentsContent(
        documents = model.documents,
        canWrite = documents.canWrite && !model.readOnly,
        readOnly = model.readOnly,
        error = if (linking) null else model.error,
        open = { documents.openDocument(it.id) },
        add = { documents.addDocument(transactionId) },
        link = {
            model.clearError()
            linking = true
        },
        retry = { scope.launch { model.retry() } },
        modifier = modifier,
    )
    if (linking) LinkDocumentDialog(model, onDismiss = { linking = false })
}

/** What [TransactionDocumentsSection] shows, without the API: for the screenshots. */
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
                    documents.value.forEach { document -> DocumentRow(document, onClick = { open(document) }) }
                }
            }
        }
        if (canWrite) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = add, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add document", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = link, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link a document", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else if (readOnly) {
            Text("You can only read documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DocumentRow(document: DocumentSummary, onClick: () -> Unit) {
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
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Link a document": a search of the documents, and a "Link" button on each. It closes when the link is made. */
@Composable
private fun LinkDocumentDialog(model: TransactionDocumentsModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        if (model.candidates != null) delay(SEARCH_DEBOUNCE_MS)
        model.search(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a document") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search documents") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (val candidates = model.candidates) {
                    // Text, not a spinner: the search answers fast, and a dialog over the sheet keeps it simple.
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
                                    onClick = { scope.launch { if (model.link(document) || model.readOnly) onDismiss() } },
                                    modifier = Modifier.semantics { contentDescription = "Link ${document.title}" },
                                ) { Text(if (linked) "Linked" else "Link") }
                            }
                        }
                    }
                }
                model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
