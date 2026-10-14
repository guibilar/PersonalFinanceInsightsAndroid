package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.daysUntil
import com.guibilar.financeinsights.core.logic.formatDay
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.ReadableColumn
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberFabExpansion
import java.time.LocalDate

/**
 * The Documents list (design canvas, "Documents"): a search, the kind chips, the documents that expire soon, then
 * all documents, newest first, a page at a time. [canWrite] false (a read-only user) hides the add actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsListScreen(
    model: DocumentsListViewModel,
    contentPadding: PaddingValues,
    open: (DocumentSummary) -> Unit,
    add: (scan: Boolean) -> Unit,
    canWrite: Boolean = true,
    today: LocalDate = LocalDate.now(),
    /** Back to where the list was opened from; none in previews and tests. */
    onBack: (() -> Unit)? = null,
) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) model.loadMore() }

    val fab = rememberFabExpansion()
    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()).nestedScroll(fab.connection)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Documents", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (canWrite) IconButton(onClick = { add(true) }) { Icon(Icons.Outlined.DocumentScanner, "Scan a document") }
                },
            )
            ReadableColumn {
                Column {
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = model::search,
                        placeholder = { Placeholder("Search titles, tags, notes and text") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (state.search.isNotEmpty()) IconButton(onClick = { model.search("") }) { Icon(Icons.Outlined.Close, "Clear search") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    KindChips(state.kind, model::setKind)
                    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().alpha(if (state.loading && state.items.isNotEmpty()) 0.5f else 1f),
                        ) {
                            when {
                                state.loading && state.items.isEmpty() -> item { Skeleton(lines = 6, lineHeight = 32.dp) }
                                state.error != null -> item { ErrorState(state.error!!, model::refresh) }
                                state.items.isEmpty() -> item {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (state.hasCriteria) {
                                            EmptyState("No documents match.")
                                            OutlinedButton(onClick = model::clearAll) { Text("Clear filters") }
                                        } else {
                                            EmptyState("No documents yet. Scan a receipt, a warranty or a lease, and find it here again by its text.")
                                            if (canWrite) OutlinedButton(onClick = { add(false) }) { Text("Add a document") }
                                        }
                                    }
                                }
                                else -> {
                                    if (state.expiring.isNotEmpty()) {
                                        item(key = "expiring-title") { SectionTitle("Expire soon") }
                                        item(key = "expiring") { ExpiringCard(state.expiring, today, open) }
                                    }
                                    item(key = "all-title") {
                                        SectionTitle(if (state.hasCriteria) "${state.totalCount} found" else "All documents")
                                    }
                                    items(state.items, key = { it.id }) { document -> DocumentRow(document, today) { open(document) } }
                                    if (state.loadingMore) {
                                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                                    } else if (state.moreFailed) {
                                        item {
                                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                TextButton(onClick = model::loadMore) { Text("Could not load more. Try again") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = { add(false) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add document") },
                expanded = fab.expanded,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).semantics { contentDescription = "Add document" },
            )
        }
    }
}

/** All, then each kind, in a row that scrolls. */
@Composable
private fun KindChips(selected: DocumentKind?, select: (DocumentKind?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(selected == null, onClick = { select(null) }, label = { Text("All") }) }
        items(DocumentKind.entries) { kind ->
            FilterChip(selected == kind, onClick = { select(kind) }, label = { Text(kind.plural) })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** The documents that expire soon, in one card: each with its day and the days left. */
@Composable
private fun ExpiringCard(documents: List<DocumentSummary>, today: LocalDate, open: (DocumentSummary) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        documents.forEachIndexed { index, document ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.fillMaxWidth().clickable { open(document) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(document.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(document.kind.label, document.expiresOn?.let { formatDay(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                daysUntil(document.expiresOn, today)?.let { ExpiryBadge(it) }
            }
        }
    }
}

/** A document in the list: its kind, title, day, files, and what to know (OCR, links, expiry). */
@Composable
private fun DocumentRow(document: DocumentSummary, today: LocalDate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindIcon(document.kind)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        document.kind.label,
                        (document.issuedOn ?: document.createdAt)?.let { formatDay(it) },
                        if (document.files.size == 1) "1 file" else "${document.files.size} files",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OcrBadge(document.ocrStatus)
                    if (document.links.isNotEmpty()) {
                        Icon(Icons.Outlined.Link, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (document.links.size == 1) "Linked" else "${document.links.size} links",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            daysUntil(document.expiresOn, today)?.takeIf { it <= 30 }?.let { ExpiryBadge(it) }
        }
    }
}
