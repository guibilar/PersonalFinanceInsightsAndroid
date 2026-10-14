package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.itemSubtitle
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.documents.DocumentItems
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.load as loadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The items of one document (T88), from the Home API of Hestia.Api (`by-link`, both sides of the link), and the
 * candidates of "Link to item". Compose state, so the section follows it; the calls are suspend functions.
 */
class DocumentItemsModel(private val api: HomeApi, val documentId: String) {
    /** The items of the document, by name. */
    var items by mutableStateOf<LoadState<List<ItemSummary>>>(LoadState.Loading)
        private set

    /** The items to pick a link from. */
    var candidates by mutableStateOf<LoadState<List<ItemSummary>>>(LoadState.Loading)
        private set

    suspend fun load() {
        val loaded = loadState(LOAD_FAILED) { api.byLink(ItemLinkKinds.DOCUMENT, documentId) }
        // A failed reload keeps the list on screen.
        if (loaded is LoadState.Failed && items is LoadState.Loaded) return
        items = loaded
    }

    /** "Retry" after a failed load. */
    suspend fun retry() {
        items = LoadState.Loading
        load()
    }

    /** The items that match [text] (all, by name, without it). */
    suspend fun search(text: String) {
        candidates = LoadState.Loading
        candidates = loadState("Could not load the items.") { api.items(q = text.trim().ifEmpty { null }, pageSize = CANDIDATES).items }
    }

    companion object {
        const val LOAD_FAILED = "Could not load the items."
        const val CANDIDATES = 20
    }
}

/**
 * The "Items" section of a document: the items of the home inventory that it belongs to (a tap opens one), and "Link to
 * item". [link] links the document to an item (kind `Home.Item`); [unlink] removes a link that the document holds.
 * [DocumentItems.canWrite] false hides both.
 */
@Composable
fun DocumentItemsSection(
    document: Document,
    items: DocumentItems,
    canWrite: Boolean,
    busy: Boolean,
    link: (ItemSummary) -> Unit,
    unlink: (DocumentLink) -> Unit,
) {
    val model = remember(document.id, items) { DocumentItemsModel(items.api, document.id) }
    val itemLinks = document.links.filter { it.kind == DocumentLinkKinds.HOME_ITEM }
    // Again after each link or unlink of the document.
    LaunchedEffect(model, itemLinks) { model.load() }
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable(document.id) { mutableStateOf(false) }
    val write = canWrite && items.canWrite
    DocumentItemsContent(
        items = model.items,
        canWrite = write,
        busy = busy,
        // Only a link that the document holds can go from here; one that the item holds goes on the item's page.
        unlinkable = { item -> itemLinks.firstOrNull { it.targetId == item.id } },
        open = { items.openItem(it.id) },
        link = { picking = true },
        unlink = unlink,
        retry = { scope.launch { model.retry() } },
    )
    if (picking && write) {
        val linked = (model.items.valueOrNull.orEmpty().map { it.id } + itemLinks.map { it.targetId }).toSet()
        ItemPickerSheet(
            model = model,
            linked = linked,
            pick = {
                picking = false
                link(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** What [DocumentItemsSection] shows, without the API: for the screenshots. */
@Composable
fun DocumentItemsContent(
    items: LoadState<List<ItemSummary>>,
    canWrite: Boolean,
    open: (ItemSummary) -> Unit,
    link: () -> Unit,
    retry: () -> Unit,
    busy: Boolean = false,
    unlinkable: (ItemSummary) -> DocumentLink? = { null },
    unlink: (DocumentLink) -> Unit = {},
) {
    SectionCard("Items", subtitle = "The things in the home that it belongs to") {
        when (items) {
            LoadState.Loading -> Text("Loading the items…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(items.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = retry) { Text("Retry") }
            }
            is LoadState.Loaded -> if (items.value.isEmpty()) {
                Text("Not linked to an item of the home.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.value.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    ItemLine(item, if (canWrite) unlinkable(item) else null, busy, open = { open(item) }, unlink = unlink)
                }
            }
        }
        if (canWrite) {
            OutlinedButton(onClick = link, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Link to item")
            }
        }
    }
}

@Composable
private fun ItemLine(item: ItemSummary, link: DocumentLink?, busy: Boolean, open: () -> Unit, unlink: (DocumentLink) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = open).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Chair, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                itemSubtitle(item, withRoom = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(Money.format(item.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (link != null) IconButton(onClick = { unlink(link) }, enabled = !busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
    }
}

/** How long the picker waits for typing to pause before it searches. */
private const val PICKER_DEBOUNCE_MS = 350L

/** "Link to item": a search over the home inventory; a tap on an item links it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPickerSheet(model: DocumentItemsModel, linked: Set<String>, pick: (ItemSummary) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        if (text.isNotBlank()) delay(PICKER_DEBOUNCE_MS)
        model.search(text)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Link to item", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Placeholder("Search the home inventory") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when (val candidates = model.candidates) {
                LoadState.Loading -> Skeleton(lines = 5, lineHeight = 28.dp, modifier = Modifier.padding(vertical = 8.dp))
                is LoadState.Failed -> ErrorState(candidates.message, onRetry = { scope.launch { model.search(text) } })
                is LoadState.Loaded -> if (candidates.value.isEmpty()) {
                    Text(
                        if (text.isBlank()) "No items yet. Add them in Home." else "No item matches “${text.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(candidates.value) { item ->
                            val done = item.id in linked
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !done) { pick(item) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(itemSubtitle(item, withRoom = true), if (done) "Linked" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
