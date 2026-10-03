package com.guibilar.financeinsights.ui.transactions

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

/** A statement file picked on the phone: its name and contents. */
class PickedFile(val name: String, val bytes: ByteArray)

/** The statement file types banks export, as phones label them. */
private val STATEMENT_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "application/csv", "text/plain",
    "application/vnd.ms-excel", "application/octet-stream",
)

/**
 * Imports a bank statement (CSV) into an account; rows imported before are skipped. When the file looks like another
 * account's, the API says so and it can go ahead anyway. [initialFile] is a file shared to the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(accounts: List<Account>, model: TransactionsViewModel, onDismiss: () -> Unit, initialFile: PickedFile? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Accounts without statement files have nothing to import into.
    val importable = accounts.filter { it.format != StatementFormat.Manual }
    var account by remember { mutableStateOf(importable.singleOrNull()) }
    // Opened from a shared statement right at launch, the accounts may arrive after the sheet.
    LaunchedEffect(importable) { if (account == null) account = importable.singleOrNull() }
    var file by remember { mutableStateOf(initialFile) }
    var error by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            mismatch = null
            val picked = withContext(Dispatchers.IO) { readFile(context, uri) }
            when {
                picked == null -> error = "Could not read that file."
                picked.bytes.size > MAX_STATEMENT_BYTES -> {
                    file = null
                    error = "The file is larger than 5 MB."
                }
                else -> file = picked
            }
        }
    }

    fun submit(confirmed: Boolean) {
        val chosen = account ?: return
        val picked = file ?: return
        uploading = true
        error = null
        mismatch = null
        scope.launch {
            when (val outcome = model.import(picked.name, picked.bytes, chosen.id, confirmed)) {
                ImportOutcome.Imported -> {
                    sheet.hide()
                    onDismiss()
                }
                is ImportOutcome.Mismatch -> mismatch = outcome.message
                is ImportOutcome.Failed -> error = outcome.message
            }
            uploading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import a statement", style = MaterialTheme.typography.titleLarge)
            Text(
                "A CSV export from your bank. Transactions imported before are skipped, so overlapping statements are fine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (importable.isEmpty()) {
                Text("No account takes statement files. Add one, or change an account's statement format, on the web.")
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            DropdownPicker<Account>(
                label = "Into account",
                options = importable,
                selected = account,
                text = { it?.let { a -> "${a.name} · ${accountTypeLabel(a.type)}" } ?: "Choose an account" },
                onPick = {
                    account = it
                    // The warning was about the previous choice.
                    mismatch = null
                },
            )
            OutlinedButton(onClick = { picker.launch(STATEMENT_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(file?.let { "${it.name} · ${(it.bytes.size + 1023) / 1024} KB" } ?: "Choose a file")
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (mismatch != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mismatch!!, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        OutlinedButton(onClick = { submit(confirmed = true) }, enabled = !uploading) { Text("Import anyway") }
                    }
                }
            }
            Button(
                onClick = { submit(confirmed = false) },
                enabled = account != null && file != null && !uploading && mismatch == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Import")
            }
            Spacer(Modifier.height(16.dp))
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
