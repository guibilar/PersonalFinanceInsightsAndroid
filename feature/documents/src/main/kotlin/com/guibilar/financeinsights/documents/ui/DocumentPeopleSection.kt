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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.people.PeopleApi
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.core.people.PersonSummary
import com.guibilar.financeinsights.core.people.groupsLine
import com.guibilar.financeinsights.documents.DocumentPeople
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.SectionCard
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.load as loadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The people of one document (T93), from the People API of Hestia.Api (`by-link`, both sides of the link), and the
 * candidates of "Link a person". Compose state, so the section follows it; the calls are suspend functions.
 */
class DocumentPeopleModel(private val api: PeopleApi, val documentId: String) {
    /** The people of the document, by name. */
    var people by mutableStateOf<LoadState<List<PersonSummary>>>(LoadState.Loading)
        private set

    /** The people to pick a link from. */
    var candidates by mutableStateOf<LoadState<List<PersonSummary>>>(LoadState.Loading)
        private set

    suspend fun load() {
        val loaded = loadState(LOAD_FAILED) { api.byLink(PersonLinkKinds.DOCUMENT, documentId) }
        // A failed reload keeps the list on screen.
        if (loaded is LoadState.Failed && people is LoadState.Loaded) return
        people = loaded
    }

    /** "Retry" after a failed load. */
    suspend fun retry() {
        people = LoadState.Loading
        load()
    }

    /** The people that match [text] (all, by name, without it). */
    suspend fun search(text: String) {
        candidates = LoadState.Loading
        candidates = loadState(LOAD_FAILED) { api.people(q = text.trim().ifEmpty { null }, pageSize = CANDIDATES).items }
    }

    companion object {
        const val LOAD_FAILED = "Could not load the people."
        const val CANDIDATES = 20
    }
}

/**
 * The "People" section of a document: the people that it is about (a tap opens one), and "Link a person". [link] links
 * the document to a person (kind `People.Person`); [unlink] removes a link that the document holds.
 * [DocumentPeople.canWrite] false hides both.
 */
@Composable
fun DocumentPeopleSection(
    document: Document,
    people: DocumentPeople,
    canWrite: Boolean,
    busy: Boolean,
    link: (PersonSummary) -> Unit,
    unlink: (DocumentLink) -> Unit,
) {
    val model = remember(document.id, people) { DocumentPeopleModel(people.api, document.id) }
    val personLinks = document.links.filter { it.kind == DocumentLinkKinds.PEOPLE_PERSON }
    // Again after each link or unlink of the document.
    LaunchedEffect(model, personLinks) { model.load() }
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable(document.id) { mutableStateOf(false) }
    val write = canWrite && people.canWrite
    DocumentPeopleContent(
        people = model.people,
        canWrite = write,
        busy = busy,
        // Only a link that the document holds can go from here; one that the person holds goes on the person's page.
        unlinkable = { person -> personLinks.firstOrNull { it.targetId == person.id } },
        open = { people.openPerson(it.id) },
        link = { picking = true },
        unlink = unlink,
        retry = { scope.launch { model.retry() } },
    )
    if (picking && write) {
        val linked = (model.people.valueOrNull.orEmpty().map { it.id } + personLinks.map { it.targetId }).toSet()
        PersonPickerSheet(
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

/** What [DocumentPeopleSection] shows, without the API: for the screenshots. */
@Composable
fun DocumentPeopleContent(
    people: LoadState<List<PersonSummary>>,
    canWrite: Boolean,
    open: (PersonSummary) -> Unit,
    link: () -> Unit,
    retry: () -> Unit,
    busy: Boolean = false,
    unlinkable: (PersonSummary) -> DocumentLink? = { null },
    unlink: (DocumentLink) -> Unit = {},
) {
    SectionCard("People", subtitle = "The people it is about") {
        when (people) {
            LoadState.Loading -> Text("Loading the people…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LoadState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(people.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = retry) { Text("Retry") }
            }
            is LoadState.Loaded -> if (people.value.isEmpty()) {
                Text("Not linked to a person.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                people.value.forEachIndexed { index, person ->
                    if (index > 0) HorizontalDivider()
                    PersonLine(person, if (canWrite) unlinkable(person) else null, busy, open = { open(person) }, unlink = unlink)
                }
            }
        }
        if (canWrite) {
            OutlinedButton(onClick = link, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Link a person")
            }
        }
    }
}

@Composable
private fun PersonLine(person: PersonSummary, link: DocumentLink?, busy: Boolean, open: () -> Unit, unlink: (DocumentLink) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = open).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            groupsLine(person).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (link != null) IconButton(onClick = { unlink(link) }, enabled = !busy) { Icon(Icons.Outlined.LinkOff, "Remove the link") }
    }
}

/** How long the picker waits for typing to pause before it searches. */
private const val PICKER_DEBOUNCE_MS = 350L

/** "Link a person": a search over the people; a tap on a person links them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonPickerSheet(model: DocumentPeopleModel, linked: Set<String>, pick: (PersonSummary) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(text) {
        if (text.isNotBlank()) delay(PICKER_DEBOUNCE_MS)
        model.search(text)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Link a person", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Placeholder("Search people") },
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
                        if (text.isBlank()) "No people yet. Add them in People." else "No person matches “${text.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(candidates.value) { person ->
                            val done = person.id in linked
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = !done) { pick(person) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(groupsLine(person), if (done) "Linked" else "").filter { it.isNotEmpty() }.joinToString(" · "),
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
