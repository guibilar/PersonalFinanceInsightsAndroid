package com.guibilar.financeinsights.ui.transactions

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.api.MAX_STATEMENT_BYTES
import com.guibilar.financeinsights.core.logic.accountTypeLabel
import com.guibilar.financeinsights.core.model.Account
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.ui.common.DropdownPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The statement file types banks export, as phones label them. */
private val STATEMENT_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "application/csv", "text/plain",
    "application/vnd.ms-excel", "application/octet-stream", "application/pdf",
)

/**
 * Imports bank statements (CSVs, or Neo or Wealthsimple PDF statements) into an account, one file after another; rows
 * imported before are skipped. A file that looks like another account's can go ahead anyway, and one that is refused
 * doesn't stop the others. The files and their uploads are the model's ([TransactionsViewModel.openImport]): they
 * outlast a rotation, and closing the sheet midway lets them finish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    accounts: List<Account>,
    model: TransactionsViewModel,
    onDismiss: () -> Unit,
    accountsLoaded: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by model.importState.collectAsState()
    // Accounts without statement files have nothing to import into.
    val importable = accounts.filter { it.format != StatementFormat.Manual }
    // The one picked, or the only one there is (the accounts may arrive after the sheet, opened from a shared statement).
    val account = importable.firstOrNull { it.id == state.accountId } ?: importable.singleOrNull()
    val files = state.files
    val uploading = state.uploading
    val pending = state.pending
    val anyImported = files.any { it.state == FileState.Imported }

    // Every file is in: the sheet goes, and the model says how it went.
    LaunchedEffect(state.allImported) {
        if (state.allImported) {
            sheet.hide()
            onDismiss()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) { uris.map { readFile(context, it) } }
            model.pickStatements(read.filterNotNull(), unreadable = read.count { it == null })
        }
    }

    fun submit() {
        val chosen = account ?: return
        model.importStatements(chosen.id)
    }

    fun importAnyway(file: PickedFile) {
        val chosen = account ?: return
        model.importAnyway(file, chosen.id)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import statements", style = MaterialTheme.typography.titleLarge)
            Text(
                "CSV exports from your bank, or Neo or Wealthsimple monthly PDF statements, one or several at a time. Transactions imported before are skipped, so overlapping statements are fine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!accountsLoaded) {
                // Not "no account takes statements" while they are still on their way.
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (importable.isEmpty()) {
                Text("No account takes statement files. Add one, or change an account's statement format, under More › Accounts.")
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            DropdownPicker<Account>(
                label = "Into account",
                options = importable,
                selected = account,
                text = { it?.let { a -> "${a.name} · ${accountTypeLabel(a.type)}" } ?: "Choose an account" },
                onPick = { model.chooseImportAccount(it.id) },
            )
            OutlinedButton(onClick = { picker.launch(STATEMENT_TYPES) }, enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (files.size) {
                        0 -> "Choose files"
                        1 -> "${files[0].file.name} · ${(files[0].file.bytes.size + 1023) / 1024} KB"
                        else -> "${files.size} files"
                    },
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (files.size > 1 || files.any { it.state != FileState.Waiting }) {
                files.forEach { entry -> FileRow(entry, enabled = !uploading, onImportAnyway = { importAnyway(entry.file) }) }
            }
            Button(
                onClick = ::submit,
                enabled = account != null && pending.isNotEmpty() && !uploading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                when {
                    uploading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    pending.size > 1 -> Text("Import ${pending.size} files")
                    files.any { it.state == FileState.Failed } -> Text("Try again")
                    else -> Text("Import")
                }
            }
            if (anyImported) {
                OutlinedButton(onClick = onDismiss, enabled = !uploading, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A file's name and how its import went; one that looks like another account's can go ahead anyway. */
@Composable
private fun FileRow(entry: FileImport, enabled: Boolean, onImportAnyway: () -> Unit) {
    val colors = when (entry.state) {
        FileState.Failed -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        FileState.Mismatch -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else -> CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                if (entry.state == FileState.Importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    when (entry.state) {
                        FileState.Waiting -> "Ready"
                        FileState.Importing -> " Importing…"
                        FileState.Imported -> entry.result!!.let { r -> "${r.added} added" + if (r.skipped > 0) ", ${r.skipped} already in" else "" }
                        FileState.Failed -> "Not imported"
                        FileState.Mismatch -> "Another account's?"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (entry.state == FileState.Imported) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
            }
            if (entry.message != null) Text(entry.message, style = MaterialTheme.typography.bodySmall)
            if (entry.state == FileState.Mismatch) {
                OutlinedButton(onClick = onImportAnyway, enabled = enabled) { Text("Import anyway") }
            }
        }
    }
}

/** The picked file's name and contents; null when it can't be read. Larger than the limit is read only that far. */
fun readFile(context: android.content.Context, uri: Uri): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "statement.csv"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() <= MAX_STATEMENT_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: return null
    PickedFile(name, bytes)
}.getOrNull()
