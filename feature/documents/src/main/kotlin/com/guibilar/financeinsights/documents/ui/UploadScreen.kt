package com.guibilar.financeinsights.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.core.documents.FileType
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.documents.formatSize
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn
import kotlinx.coroutines.launch

/**
 * A new document: scan it, or pick photos or files, then give its details and upload it. [start] opens that source
 * at once (Capture's "Scan" and "Photo"). After the upload, [uploaded] opens the new document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    model: UploadViewModel,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    uploaded: (id: String) -> Unit,
    start: DocumentSource? = null,
) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pickers = rememberDocumentPickers(
        onPreparing = { model.preparing(true) },
        onPicked = model::add,
        onMessage = { scope.launch { snackbar.showSnackbar(it) } },
    )
    // The source asked for opens once, not again after a rotation.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start != null && !started) {
            started = true
            pickers.launch(start)
        }
    }
    LaunchedEffect(state.uploaded) { state.uploaded?.let { uploaded(it.id) } }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { FitText("Add document", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } },
            )
            ReadableColumn {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourceButton(Icons.Outlined.DocumentScanner, "Scan", Modifier.weight(1f), enabled = !state.uploading) { pickers.launch(DocumentSource.Scan) }
                        SourceButton(Icons.Outlined.Image, "Photos", Modifier.weight(1f), enabled = !state.uploading) { pickers.launch(DocumentSource.Photos) }
                        SourceButton(Icons.Outlined.Folder, "Files", Modifier.weight(1f), enabled = !state.uploading) { pickers.launch(DocumentSource.Files) }
                    }
                    FilesCard(state.files, state.preparing, remove = model::remove, enabled = !state.uploading)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    DetailsForm(state.draft, model::edit, titleHint = state.titleHint)
                    Button(
                        onClick = model::upload,
                        enabled = state.files.isNotEmpty() && !state.uploading && !state.preparing,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.uploading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.size(8.dp))
                            Text("Uploading…")
                        } else {
                            Text("Upload")
                        }
                    }
                    Text(
                        "The text of the files is read on the server after the upload, so you can find the document by its words.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SourceButton(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, maxLines = 1)
    }
}

/** The files to upload, each with its size and a button to take it out. */
@Composable
private fun FilesCard(files: List<UploadFile>, preparing: Boolean, remove: (UploadFile) -> Unit, enabled: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (files.isEmpty() && !preparing) {
            Text(
                "No files yet. Scan the document, or pick photos or PDFs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        files.forEachIndexed { index, file ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (file.type == FileType.Pdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(file.type?.extension?.uppercase(), formatSize(file.size)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { remove(file) }, enabled = enabled) { Icon(Icons.Outlined.Close, "Remove ${file.name}") }
            }
        }
        if (preparing) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Reading the files…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
