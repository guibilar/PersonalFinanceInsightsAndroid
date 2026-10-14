package com.guibilar.financeinsights.documents.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentDetails
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.documents.daysUntil
import com.guibilar.financeinsights.core.documents.formatSize
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.documents.DocumentItems
import com.guibilar.financeinsights.documents.DocumentTasks
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The longest side of an image shown in the app: sharp on a phone, light on memory. */
private const val VIEW_SIDE = 2048

/** The lines of OCR text shown before "Show all". */
private const val OCR_PREVIEW_LINES = 6

/**
 * One document (design canvas, "Documents"): its details, its files, its text, and its links. [canWrite] false (a
 * read-only user) hides edit, delete, link, and the OCR retry. [openTransaction] opens a linked transaction. [tasks]
 * shows the "Tasks" section (T84), and [items] the "Items" section (T88); none hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    model: DocumentViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    openTransaction: (String) -> Unit,
    canWrite: Boolean = true,
    today: LocalDate = LocalDate.now(),
    tasks: DocumentTasks? = null,
    items: DocumentItems? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var unlinking by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val document = state.document.valueOrNull
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun show(file: DocumentFile) {
        if (file.isImage) {
            viewing = file.id
            return
        }
        scope.launch {
            val result = model.download(context, file)
            val message = result.fold(
                onSuccess = { if (openFile(context, it, file.contentType)) null else "No app on this phone can open ${file.fileName}." },
                onFailure = { (it as? ApiError)?.loadMessage("Could not download ${file.fileName}.") ?: "Could not download ${file.fileName}." },
            )
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText(document?.title ?: "Document", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite && document != null) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Edit details") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = {
                                        menu = false
                                        deleting = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
            ReadableColumn {
                when (val loaded = state.document) {
                    LoadState.Loading -> Box(Modifier.padding(16.dp)) { Skeleton(lines = 8, lineHeight = 24.dp) }
                    is LoadState.Failed -> ErrorState(loaded.message, model::reload, Modifier.padding(16.dp))
                    is LoadState.Loaded -> PullToRefreshBox(isRefreshing = false, onRefresh = model::reload, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DetailsCard(loaded.value, today)
                            FilesCard(loaded.value.files, ::show)
                            TextCard(loaded.value, state.ocrSlow, canWrite, state.busy, model::retryOcr)
                            LinksCard(
                                links = loaded.value.transactionLinks,
                                transactions = state.transactions,
                                canWrite = canWrite,
                                busy = state.busy,
                                open = openTransaction,
                                unlink = { unlinking = it.id },
                                add = { model.findCandidates() },
                            )
                            if (tasks != null) DocumentTasksSection(loaded.value, tasks, today, canWrite)
                            if (items != null) {
                                DocumentItemsSection(
                                    document = loaded.value,
                                    items = items,
                                    canWrite = canWrite,
                                    busy = state.busy,
                                    link = { model.linkItem(it.id, it.name) },
                                    unlink = { unlinking = it.id },
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editing && document != null) {
        EditSheet(document, onSave = model::save, onDismiss = { editing = false })
    }
    if (deleting && document != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete document?") },
            text = { Text("“${document.title}” and its files will be deleted. Its transactions stay.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    model.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    unlinking?.let { linkId ->
        val link = document?.links?.firstOrNull { it.id == linkId }
        if (link == null) {
            unlinking = null
        } else {
            val transaction = (state.transactions[link.targetId] as? LinkedTransaction.Found)?.transaction
            val other = if (link.kind == DocumentLinkKinds.HOME_ITEM) "the item" else transaction?.description ?: "the transaction"
            AlertDialog(
                onDismissRequest = { unlinking = null },
                title = { Text("Remove the link?") },
                text = { Text("The document and $other both stay. Only the link goes.") },
                confirmButton = {
                    TextButton(onClick = {
                        unlinking = null
                        model.unlink(link)
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Cancel") } },
            )
        }
    }
    state.candidates?.let { candidates ->
        LinkSheet(
            candidates = candidates,
            linked = document?.transactionLinks.orEmpty().map { it.targetId }.toSet(),
            search = model::findCandidates,
            pick = { transaction ->
                model.closeCandidates()
                model.link(transaction)
            },
            onDismiss = model::closeCandidates,
        )
    }
    viewing?.let { fileId ->
        val file = document?.files?.firstOrNull { it.id == fileId }
        if (file == null) {
            viewing = null
        } else {
            ImageViewer(model, file, onDismiss = { viewing = null }, openElsewhere = {
                scope.launch {
                    model.download(context, file).getOrNull()?.let { openFile(context, it, file.contentType) }
                }
            })
        }
    }
}

/** The kind, the dates with the days to the expiry, the tags and the notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(document: Document, today: LocalDate) {
    // The title is in the top bar: the card starts with the kind.
    SectionCard("Details", subtitle = document.kind.label, action = { KindIcon(document.kind) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            document.issuedOn?.let { Fact("Issued", formatDay(it)) }
            document.expiresOn?.let { expires ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Fact("Expires", formatDay(expires), Modifier.weight(1f))
                    daysUntil(expires, today)?.let { ExpiryBadge(it) }
                }
            }
            if (document.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    document.tags.forEach { tag ->
                        Badge(tag, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            document.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (document.issuedOn == null && document.expiresOn == null && document.tags.isEmpty() && document.notes.isNullOrBlank()) {
                Text("No dates, tags or notes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The files: a tap opens one (an image in the app, a PDF in a viewer). */
@Composable
private fun FilesCard(files: List<DocumentFile>, open: (DocumentFile) -> Unit) {
    SectionCard(if (files.size == 1) "1 file" else "${files.size} files") {
        files.forEachIndexed { index, file ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { open(file) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (file.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(file.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(if (file.isPdf) "PDF" else file.contentType.substringAfter('/').uppercase(), formatSize(file.size)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open ${file.fileName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The text that the OCR found, collapsed; or where the OCR is. */
@Composable
private fun TextCard(document: Document, slow: Boolean, canWrite: Boolean, busy: Boolean, retry: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard("Text", subtitle = "Found by the OCR, and searched") {
        when (document.ocrStatus) {
            OcrStatus.Pending -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (slow) "This takes longer than usual. Come back later: the text will be here." else "Reading the text of the files…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OcrStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The text could not be read.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                document.ocrError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (canWrite) OutlinedButton(onClick = retry, enabled = !busy) { Text("Run OCR again") }
            }
            OcrStatus.NotNeeded, OcrStatus.Unknown -> Text(
                "No text recognition for this document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OcrStatus.Done -> {
                val text = document.ocrText?.trim().orEmpty()
                if (text.isEmpty()) {
                    Text("The files have no text.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val long = text.lines().size > OCR_PREVIEW_LINES || text.length > 400
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = if (expanded || !long) Int.MAX_VALUE else OCR_PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (long) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show all") }
                }
            }
        }
    }
}

/** The transactions that the document belongs to: each opens in Money. */
@Composable
private fun LinksCard(
    links: List<DocumentLink>,
    transactions: Map<String, LinkedTransaction>,
    canWrite: Boolean,
    busy: Boolean,
    open: (String) -> Unit,
    unlink: (DocumentLink) -> Unit,
    add: () -> Unit,
) {
    SectionCard("Transactions", subtitle = "What this document belongs to in Money") {
        if (links.isEmpty()) {
            Text("Not linked to a transaction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        links.forEachIndexed { index, link ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { open(link.targetId) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    when (val found = transactions[link.targetId]) {
                        is LinkedTransaction.Found -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    found.transaction.description,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(Money.format(found.transaction.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                listOfNotNull(formatDay(found.transaction.date), found.transaction.accountName.ifBlank { null }, if (found.deleted) "Deleted" else null)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinkedTransaction.Missing -> Text("A transaction that Money no longer has", style = MaterialTheme.typography.bodyMedium)
                        LinkedTransaction.Failed -> Text("Could not load the transaction", style = MaterialTheme.typography.bodyMedium)
                        LinkedTransaction.Loading, null -> Skeleton(lines = 2, lineHeight = 12.dp)
                    }
                }
                if (canWrite) {
                    IconButton(onClick = { unlink(link) }, enabled = !busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
                }
            }
        }
        if (canWrite) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = add, enabled = !busy) {
                Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Link to a transaction")
            }
        }
    }
}

/** Picks a transaction to link: the latest ones, or a search by description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(
    candidates: LoadState<List<Transaction>>,
    linked: Set<String>,
    search: (String) -> Unit,
    pick: (Transaction) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Link to a transaction", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    search(it)
                },
                placeholder = { Placeholder("Search descriptions") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when (candidates) {
                LoadState.Loading -> Skeleton(lines = 5, lineHeight = 28.dp, modifier = Modifier.padding(vertical = 8.dp))
                is LoadState.Failed -> ErrorState(candidates.message, onRetry = { search(text) })
                is LoadState.Loaded -> if (candidates.value.isEmpty()) {
                    Text(
                        if (text.isBlank()) "No transactions yet." else "No transactions match “${text.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(candidates.value, key = { it.id }) { transaction ->
                            val already = transaction.id in linked
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !already) { pick(transaction) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(formatDay(transaction.date), transaction.accountName.ifBlank { null }, if (already) "Linked" else null)
                                            .joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(transaction.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The details form in a sheet; Save sends all of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(document: Document, onSave: suspend (DocumentDetails) -> String?, onDismiss: () -> Unit) {
    var draft by rememberSaveable(document.id, stateSaver = DetailsDraft.Saver) { mutableStateOf(DetailsDraft.of(document)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleMissing = draft.title.isBlank()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit details", style = MaterialTheme.typography.titleLarge)
            DetailsForm(draft, { draft = it }, titleError = if (titleMissing) "Enter a title." else null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            error = onSave(draft.toDetails())
                            saving = false
                            if (error == null) {
                                sheet.hide()
                                onDismiss()
                            }
                        }
                    },
                    enabled = !saving && !titleMissing && draft.expiryError == null,
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** An image file, full screen, in the app; "Open in another app" for more. */
@Composable
private fun ImageViewer(model: DocumentViewModel, file: DocumentFile, onDismiss: () -> Unit, openElsewhere: () -> Unit) {
    val context = LocalContext.current
    var image by remember(file.id) { mutableStateOf<LoadState<Bitmap>>(LoadState.Loading) }
    LaunchedEffect(file.id) {
        val downloaded = model.download(context, file)
        image = downloaded.fold(
            onSuccess = { saved ->
                val bitmap = withContext(Dispatchers.Default) { decodeUpright(saved.readBytes(), VIEW_SIDE) }
                if (bitmap != null) LoadState.Loaded(bitmap) else LoadState.Failed("This phone can't show ${file.fileName}. Open it in another app.")
            },
            onFailure = { LoadState.Failed((it as? ApiError)?.loadMessage("Could not download ${file.fileName}.") ?: "Could not download ${file.fileName}.") },
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
                Text(
                    file.fileName,
                    Modifier.weight(1f),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = openElsewhere) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in another app", tint = Color.White)
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val loaded = image) {
                    LoadState.Loading -> CircularProgressIndicator()
                    is LoadState.Failed -> Text(loaded.message, color = Color.White)
                    is LoadState.Loaded -> Image(
                        loaded.value.asImageBitmap(),
                        contentDescription = file.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
