package com.guibilar.financeinsights.people.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guibilar.financeinsights.core.people.PeopleImportItem
import com.guibilar.financeinsights.core.people.importActionText
import com.guibilar.financeinsights.core.people.importCounts
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The biggest vCard file that the screen sends (the server takes 20 million characters; photos make files big). */
const val MAX_VCARD_BYTES = 20_000_000

/**
 * Import contacts (T105): the phone's contacts (one tap, after the permission) or a vCard file; then the preview of the
 * server (new people, details added to people who are here, the same, skipped with the reason), with a box for each, and
 * "Import". [sharedVCard] holds the text of a .vcf shared to the app (T101): the screen shows its preview, then clears it.
 * [readContacts] reads the phone's contacts (tests give their own).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleImportScreen(
    model: PeopleImportViewModel,
    contentPadding: PaddingValues,
    canWrite: Boolean,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    sharedVCard: MutableStateFlow<String?> = remember { MutableStateFlow(null) },
    readContacts: (suspend () -> List<com.guibilar.financeinsights.core.people.PersonDetails>)? = null,
) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.done.collect(onDone) }
    val shared by sharedVCard.collectAsState()
    LaunchedEffect(shared) {
        shared?.let {
            sharedVCard.value = null
            if (canWrite) model.fromVCard(it)
        }
    }

    fun loadPhone() {
        scope.launch {
            val people = readContacts?.invoke() ?: withContext(Dispatchers.IO) { readPhoneContacts(context.contentResolver) }
            model.fromPhone(people)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadPhone() else model.failed("Hestia needs to read your contacts to import them. You can allow it in Settings.")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(64 * 1024)
                            while (out.size() <= MAX_VCARD_BYTES) {
                                val read = stream.read(buffer, 0, minOf(buffer.size, MAX_VCARD_BYTES + 1 - out.size()))
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                            if (out.size() > MAX_VCARD_BYTES) null else out.toString(Charsets.UTF_8.name())
                        }
                    }.getOrNull()
                }
                if (text == null) model.failed("Could not read that file (at most 20 MB).") else model.fromVCard(text)
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Import contacts", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
            ReadableColumn {
                val preview = state.preview
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!canWrite) {
                        item { Text("You can only read people.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else if (preview == null) {
                        item {
                            Text(
                                "Add the people you care about at once. Nothing you wrote is overwritten: a person who is here already only gets the details that are missing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            SourceButton(Icons.Outlined.Contacts, "From this phone's contacts", "Choose who to add in the next step.", !state.busy) {
                                if (readContacts != null ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    loadPhone()
                                } else {
                                    permission.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        }
                        item {
                            SourceButton(Icons.Outlined.FileOpen, "From a .vcf file", "Exported from iCloud, Google Contacts, or another phone.", !state.busy) {
                                pickFile.launch("*/*")
                            }
                        }
                        if (state.busy) item { Text("Reading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        item(key = "counts") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(importCounts(preview), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (state.choosable.isNotEmpty()) {
                                    TextButton(onClick = model::toggleAll) { Text(if (state.allChosen) "Choose none" else "Choose all") }
                                }
                            }
                        }
                        items(preview.people, key = { it.index }) { item ->
                            ContactRow(item, chosen = item.index in state.chosen, onToggle = { model.toggle(item.index) })
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (canWrite && state.preview != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            ) {
                OutlinedButton(onClick = model::startOver, enabled = !state.busy) { Text("Start over") }
                Spacer(Modifier.weight(1f))
                Button(onClick = model::confirm, enabled = !state.busy && state.chosen.isNotEmpty()) {
                    val count = state.chosen.size
                    Text(if (state.busy) "Importing…" else "Import $count ${if (count == 1) "contact" else "contacts"}")
                }
            }
        }
    }
}

@Composable
private fun SourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(20.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContactRow(item: PeopleImportItem, chosen: Boolean, onToggle: () -> Unit) {
    val person = item.person
    val details = listOfNotNull(
        person.email,
        person.phone,
        person.birthday?.let { b -> listOfNotNull(java.time.Month.of(b.month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US) + " " + b.day, b.year?.toString()).joinToString(", ") },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(enabled = item.choosable, role = Role.Checkbox, onClick = onToggle)
            .alpha(if (item.choosable) 1f else 0.6f)
            .padding(end = 12.dp),
    ) {
        Checkbox(checked = chosen, onCheckedChange = { onToggle() }, enabled = item.choosable)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(person.name.ifBlank { "(No name)" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                importActionText(item),
                style = MaterialTheme.typography.labelMedium,
                color = when (item.action) {
                    "Add" -> MaterialTheme.colorScheme.primary
                    "Merge" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
