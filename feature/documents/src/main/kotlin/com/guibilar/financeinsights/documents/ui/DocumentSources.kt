package com.guibilar.financeinsights.documents.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.guibilar.financeinsights.core.documents.MAX_FILES
import com.guibilar.financeinsights.core.documents.UploadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the files of a new document come from. */
enum class DocumentSource {
    /** The ML Kit document scanner: the camera, page edges found, as one PDF. */
    Scan,

    /** The system photo picker. */
    Photos,

    /** The system file picker: PDFs and images. */
    Files,
}

/** Starts each way to pick the files of a document. */
@Stable
class DocumentPickers internal constructor(private val scan: () -> Unit, private val photos: () -> Unit, private val files: () -> Unit) {
    fun launch(source: DocumentSource) = when (source) {
        DocumentSource.Scan -> scan()
        DocumentSource.Photos -> photos()
        DocumentSource.Files -> files()
    }
}

private val scanName = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm", Locale.US)

/**
 * The scanner, the photo picker and the file picker. What they give is read and, for photos, made small
 * ([preparePhoto]) off the main thread, then handed to [onPicked]. [onPreparing] says that reading has begun.
 * When the phone has no scanner (no Google Play services, or too little memory), the file picker opens instead and
 * [onMessage] says why.
 */
@Composable
fun rememberDocumentPickers(
    onPreparing: () -> Unit,
    onPicked: (files: List<UploadFile>, unreadable: Int) -> Unit,
    onMessage: (String) -> Unit,
): DocumentPickers {
    val context = LocalContext.current
    val activity: Activity? = LocalActivity.current
    val scope = rememberCoroutineScope()

    fun take(uris: List<Uri>, name: (Int) -> String) {
        if (uris.isEmpty()) return
        onPreparing()
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                uris.mapIndexed { index, uri -> readPicked(context, uri, name(index))?.let(::preparePhoto) }
            }
            onPicked(read.filterNotNull(), read.count { it == null })
        }
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_FILES)) { uris ->
        take(uris) { "photo-${it + 1}.jpg" }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        take(uris) { "document-${it + 1}" }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri ?: return@rememberLauncherForActivityResult
        // The scanner names its file by a number: the scan's time says more.
        val name = "Scan ${LocalDateTime.now().format(scanName)}.pdf"
        onPreparing()
        scope.launch {
            val read = withContext(Dispatchers.IO) { readPicked(context, pdf, name) }
            onPicked(listOfNotNull(read?.let { UploadFile(name, it.bytes) }), if (read == null) 1 else 0)
        }
    }

    fun openFiles() = files.launch(arrayOf("application/pdf", "image/*"))

    fun openScanner() {
        if (activity == null) {
            openFiles()
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(MAX_SCAN_PAGES)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener {
                onMessage("The scanner is not available on this phone. Pick a PDF or a photo instead.")
                openFiles()
            }
    }

    return DocumentPickers(
        scan = ::openScanner,
        photos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        files = ::openFiles,
    )
}

/** The most pages in one scan: a long lease or a manual. */
private const val MAX_SCAN_PAGES = 30
