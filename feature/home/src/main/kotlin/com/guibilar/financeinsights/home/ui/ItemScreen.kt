package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.home.Item
import com.guibilar.financeinsights.core.home.ItemDocument
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.ItemLink
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.ItemTask
import com.guibilar.financeinsights.core.home.Warranty
import com.guibilar.financeinsights.core.home.daysLeftText
import com.guibilar.financeinsights.core.home.repeatText
import com.guibilar.financeinsights.core.home.roomLabel
import com.guibilar.financeinsights.core.home.warrantyOf
import com.guibilar.financeinsights.core.home.warrantyText
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.home.HomeHost
import com.guibilar.financeinsights.ui.common.DateField
import com.guibilar.financeinsights.ui.common.ChoiceRow
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.ShowMessages
import com.guibilar.financeinsights.ui.common.Skeleton
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One item (design canvas, "Home"): its photo, its details and warranty, its documents (a photo shows as a picture),
 * its purchase, and its maintenance tasks. A tap on a link opens it in its module ([host]). [canWrite] false (a
 * read-only user) hides edit, delete, the links, new photos and new maintenance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(
    model: ItemViewModel,
    host: HomeHost,
    contentPadding: PaddingValues,
    today: LocalDate,
    onBack: () -> Unit,
    canWrite: Boolean = true,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    ShowMessages(model.messages, snackbar)
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var unlinking by rememberSaveable { mutableStateOf<String?>(null) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // What the next photo is: the item, or its receipt. Kept while the camera app is open.
    var photoKind by rememberSaveable { mutableStateOf(PhotoKind.ItemPhoto) }
    val pickers = rememberPhotoPickers(host, onPreparing = model::preparing) { model.addPhoto(it, photoKind) }
    val item = state.item.valueOrNull

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText(item?.name ?: "Item", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite && item != null) {
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
                when (val loaded = state.item) {
                    LoadState.Loading -> Box(Modifier.padding(16.dp)) { Skeleton(lines = 8, lineHeight = 24.dp) }
                    is LoadState.Failed -> ErrorState(loaded.message, model::reload, Modifier.padding(16.dp))
                    is LoadState.Loaded -> PullToRefreshBox(isRefreshing = false, onRefresh = model::reload, modifier = Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.cover?.let { (documentId, bitmap) ->
                                Image(
                                    bitmap.asImageBitmap(),
                                    contentDescription = "Photo of ${loaded.value.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                                        .clickable { host.openDocument(documentId) },
                                )
                            }
                            DetailsCard(loaded.value, today)
                            DocumentsCard(
                                item = loaded.value,
                                photos = state.photos,
                                canWrite = canWrite,
                                busy = state.busy || state.uploading,
                                uploading = state.uploading,
                                open = host::openDocument,
                                unlink = { unlinking = it.id },
                                addPhoto = { kind, source ->
                                    photoKind = kind
                                    pickers.launch(source)
                                },
                                link = { model.openPicker(ItemPicker.Document) },
                            )
                            PurchaseCard(
                                links = loaded.value.links.filter { it.kind == ItemLinkKinds.TRANSACTION },
                                transactions = state.transactions,
                                canWrite = canWrite,
                                busy = state.busy,
                                open = host::openTransaction,
                                unlink = { unlinking = it.id },
                                link = { model.openPicker(ItemPicker.Transaction) },
                            )
                            MaintenanceSection(loaded.value.maintenance, today, canWrite, open = host::openTask, add = { addingTask = true })
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editing && item != null) {
        EditSheet(item, state.rooms, onSave = model::save, onDismiss = { editing = false })
    }
    if (deleting && item != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete the item?") },
            text = { Text("“${item.name}” and its links will be deleted. Its documents, purchase and tasks stay.") },
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
        val link = item?.links?.firstOrNull { it.id == linkId }
        if (link == null) {
            unlinking = null
        } else {
            AlertDialog(
                onDismissRequest = { unlinking = null },
                title = { Text("Remove the link?") },
                text = { Text("The item and the linked document or purchase both stay. Only the link goes.") },
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
    if (addingTask && item != null) {
        MaintenanceSheet(item.name, today, save = model::addMaintenance, onDismiss = { addingTask = false })
    }
    when (state.picker) {
        ItemPicker.Document -> PickerSheet(
            title = "Link a document",
            searchHint = "Search documents",
            candidates = state.documentCandidates,
            empty = { search -> if (search.isBlank()) "No documents yet." else "No document matches “${search.trim()}”." },
            search = model::search,
            onDismiss = model::closePicker,
        ) { document: DocumentSummary ->
            val linked = item?.documents.orEmpty().any { it.id == document.id }
            CandidateRow(document.title, listOfNotNull(document.kind.label, if (linked) "Linked" else null).joinToString(" · "), null, enabled = !linked) {
                model.closePicker()
                model.linkDocument(document)
            }
        }
        ItemPicker.Transaction -> PickerSheet(
            title = "Link the purchase",
            searchHint = "Search descriptions",
            candidates = state.transactionCandidates,
            empty = { search -> if (search.isBlank()) "No transactions yet." else "No transactions match “${search.trim()}”." },
            search = model::search,
            onDismiss = model::closePicker,
        ) { transaction: Transaction ->
            val linked = item?.links.orEmpty().any { it.kind == ItemLinkKinds.TRANSACTION && it.targetId == transaction.id }
            CandidateRow(
                transaction.description,
                listOfNotNull(formatDay(transaction.date), transaction.accountName.ifBlank { null }, if (linked) "Linked" else null).joinToString(" · "),
                Money.format(transaction.amount),
                enabled = !linked,
            ) {
                model.closePicker()
                model.linkTransaction(transaction)
            }
        }
        null -> {}
    }
}

/** The warranty, then the details as label and value. */
@Composable
private fun DetailsCard(item: Item, today: LocalDate) {
    val warranty = warrantyOf(item.warrantyUntil, today)
    SectionCard("Details", subtitle = roomLabel(item.roomName)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            warrantyText(warranty)?.let { text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp), tint = warrantyColor(warranty))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (warranty is Warranty.EndsSoon) FontWeight.SemiBold else null,
                        color = warrantyColor(warranty),
                    )
                }
            }
            Fact("Value", Money.format(item.value), strong = true)
            item.category?.let { Fact("Category", it) }
            listOfNotNull(item.brand, item.model).joinToString(" ").ifBlank { null }?.let { Fact("Brand", it) }
            item.serialNumber?.let { Fact("Serial no.", it) }
            if (item.quantity != 1) Fact("Quantity", item.quantity.toString())
            item.purchasedOn?.let { Fact("Bought", formatDay(it)) }
            item.purchasePrice?.let { Fact(if (item.quantity > 1) "Price of one" else "Price", Money.format(it)) }
            item.currentValue?.let { Fact("Value now", Money.format(it)) }
            if (item.tags.isNotEmpty()) Fact("Tags", item.tags.joinToString(", "))
            item.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, strong: Boolean = false) {
    Row {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.SemiBold else null)
    }
}

/** The documents: a photo shows as a small picture; a tap opens the document. "Add receipt or photo" and "Link a document". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentsCard(
    item: Item,
    photos: Map<String, Photo>,
    canWrite: Boolean,
    busy: Boolean,
    uploading: Boolean,
    open: (String) -> Unit,
    unlink: (ItemLink) -> Unit,
    addPhoto: (PhotoKind, PhotoSource) -> Unit,
    link: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard("Documents", subtitle = "Receipts, manuals, warranties and photos") {
        if (item.documents.isEmpty()) {
            Text("No documents yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.documents.forEachIndexed { index, document ->
            if (index > 0) HorizontalDivider()
            // Only the item's own link can go from here; a link that the document made goes on the document's page.
            val own = item.links.firstOrNull { it.kind == ItemLinkKinds.DOCUMENT && it.targetId == document.id }
            DocumentRow(document, photos[document.id], canWrite && own != null, busy, open = { open(document.id) }, unlink = { own?.let(unlink) })
        }
        if (uploading) {
            Text("Uploading the photo…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canWrite) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = !busy) {
                        Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add receipt or photo")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(
                            "Take a photo of the item" to (PhotoKind.ItemPhoto to PhotoSource.Camera),
                            "Pick a photo of the item" to (PhotoKind.ItemPhoto to PhotoSource.Photos),
                            "Take a photo of the receipt" to (PhotoKind.Receipt to PhotoSource.Camera),
                            "Pick a photo of the receipt" to (PhotoKind.Receipt to PhotoSource.Photos),
                        ).forEach { (label, choice) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                menu = false
                                addPhoto(choice.first, choice.second)
                            })
                        }
                    }
                }
                OutlinedButton(onClick = link, enabled = !busy) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Link a document")
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: ItemDocument, photo: Photo?, canUnlink: Boolean, busy: Boolean, open: () -> Unit, unlink: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = open).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo is Photo.Image) {
            Image(
                photo.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(document.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(document.kind.ifBlank { null }, document.expiresOn?.let { "expires ${formatDay(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canUnlink) IconButton(onClick = unlink, enabled = !busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
    }
}

/** The purchase: each linked transaction, which a tap opens in Money; and "Link the purchase". */
@Composable
private fun PurchaseCard(
    links: List<ItemLink>,
    transactions: Map<String, Linked<Transaction>>,
    canWrite: Boolean,
    busy: Boolean,
    open: (String) -> Unit,
    unlink: (ItemLink) -> Unit,
    link: () -> Unit,
) {
    SectionCard("Purchase") {
        if (links.isEmpty()) {
            Text("Not linked to a transaction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        links.forEach { itemLink ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open(itemLink.targetId) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { TransactionLine(transactions[itemLink.targetId]) }
                if (canWrite) IconButton(onClick = { unlink(itemLink) }, enabled = !busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
            }
        }
        if (canWrite) {
            OutlinedButton(onClick = link, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Link the purchase")
            }
        }
    }
}

@Composable
private fun TransactionLine(found: Linked<Transaction>?) {
    when (found) {
        is Linked.Found -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(found.value.description, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.format(found.value.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                listOfNotNull("Money", formatDay(found.value.date), found.value.accountName.ifBlank { null }, if (found.deleted) "Deleted" else null)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Linked.Missing -> Text("A transaction that Money no longer has", style = MaterialTheme.typography.bodyMedium)
        Linked.Failed -> Text("Could not load the transaction", style = MaterialTheme.typography.bodyMedium)
        Linked.Loading, null -> Skeleton(lines = 2, lineHeight = 12.dp)
    }
}

/** The maintenance tasks: each with its repeat and its next date; a tap opens it in Tasks. "Add maintenance". */
@Composable
private fun MaintenanceSection(tasks: List<ItemTask>, today: LocalDate, canWrite: Boolean, open: (String) -> Unit, add: () -> Unit) {
    SectionCard("Maintenance", subtitle = "Jobs that keep it working") {
        if (tasks.isEmpty()) {
            Text("No maintenance tasks.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tasks.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open(task.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Autorenew, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val details = listOfNotNull(
                        repeatText(task.repeatEvery, task.repeatUnit),
                        if (task.isDone) "Done" else task.dueOn?.let { "next ${formatDay(it, year = false)}" },
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!task.isDone) daysLeftText(task.dueOn, today)?.let { Badge(it, strong = false) }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canWrite) {
            OutlinedButton(onClick = add, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Autorenew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add maintenance")
            }
        }
    }
}

/** A new maintenance task in a sheet: what to do, how often (after each done), and the first day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceSheet(
    itemName: String,
    today: LocalDate,
    save: suspend (String, Int?, RepeatUnit, LocalDate) -> String?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("Clean the $itemName") }
    var every by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(RepeatUnit.Months) }
    var first by rememberSaveable { mutableStateOf(today.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New maintenance task", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What to do") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = every,
                onValueChange = { text -> every = text.filter { it.isDigit() }.take(4) },
                label = { Text("Every") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            ChoiceRow(RepeatUnit.entries, unit, label = { u -> u.many.replaceFirstChar { it.uppercase() } }, onSelect = { unit = it })
            DateField("First due", LocalDate.parse(first), onChange = { first = it.toString() }, modifier = Modifier.fillMaxWidth())
            Text(
                "The next date counts from the day you mark it done. The task shows in Tasks and on the Home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            error = save(title, every.toIntOrNull(), unit, LocalDate.parse(first))
                            saving = false
                            if (error == null) onDismiss()
                        }
                    },
                ) { Text("Add the task") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The item's details in a sheet: the form, and Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(item: Item, rooms: List<com.guibilar.financeinsights.core.home.Room>, onSave: suspend (ItemDraft) -> String?, onDismiss: () -> Unit) {
    var draft by rememberSaveable(item.id, item.updatedAt, stateSaver = ItemDraftSaver) { mutableStateOf(ItemDraft.of(item)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit details", style = MaterialTheme.typography.titleLarge)
            ItemForm(draft, rooms, onChange = { draft = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            error = onSave(draft)
                            saving = false
                            if (error == null) onDismiss()
                        }
                    },
                    enabled = !saving && draft.canSave,
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
