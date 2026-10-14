package com.guibilar.financeinsights.documents.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.FileType
import com.guibilar.financeinsights.core.documents.MAX_FILE_BYTES
import com.guibilar.financeinsights.core.documents.MAX_IMAGE_SIDE
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.documents.scaledSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

// The files of documents on the phone: reading what was picked, making photos small for the upload, and opening a
// document's file in another app from the cache.

/** The JPEG quality of a scaled photo: the text stays sharp for the OCR, at a fraction of the size. */
private const val JPEG_QUALITY = 85

/**
 * A picked file's name and content; null when it can't be read. A file larger than the server takes is read only a
 * little past the limit, so the check before the upload refuses it without the whole file in memory.
 */
fun readPicked(context: Context, uri: Uri, fallbackName: String = "document"): UploadFile? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: fallbackName
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= MAX_FILE_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: return null
    UploadFile(name, bytes)
}.getOrNull()

/**
 * A photo as the app uploads it: upright, at most [maxSide] pixels on its long side, as JPEG. A PDF, a file that
 * is not an image, and a small upright JPEG go as they are; so does an image that this phone can't decode (an
 * older phone and HEIC): the server reads it.
 */
fun preparePhoto(file: UploadFile, maxSide: Int = MAX_IMAGE_SIDE): UploadFile {
    val type = file.type
    if (type == null || !type.isImage) return file
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return file
    val orientation = orientation(file.bytes)
    val small = maxOf(bounds.outWidth, bounds.outHeight) <= maxSide
    if (small && type == FileType.Jpeg && orientation == ExifInterface.ORIENTATION_NORMAL) return file
    val bitmap = decodeUpright(file.bytes, maxSide) ?: return file
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    bitmap.recycle()
    return UploadFile(jpegName(file.name), out.toByteArray())
}

/** The name of a photo after it became a JPEG: "IMG_1234.heic" → "IMG_1234.jpg". */
fun jpegName(name: String): String {
    val stem = name.substringBeforeLast('.', name).ifBlank { "photo" }
    return "$stem.jpg"
}

/** An image upright and at most [maxSide] on its long side; null when it can't be decoded. */
fun decodeUpright(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    // A power of two that keeps the image at least as large as the target: decoding stays cheap on memory.
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val (targetWidth, targetHeight) = scaledSize(decoded.width, decoded.height, maxSide)
    val scaled = if (targetWidth != decoded.width || targetHeight != decoded.height) {
        decoded.scale(targetWidth, targetHeight).also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }
    val matrix = uprightMatrix(orientation(bytes)) ?: return scaled
    return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true).also { if (it !== scaled) scaled.recycle() }
}

/** The EXIF orientation; an image without one (or with "undefined") is upright. */
private fun orientation(bytes: ByteArray): Int = runCatching {
    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL).takeIf { it != ExifInterface.ORIENTATION_UNDEFINED } ?: ExifInterface.ORIENTATION_NORMAL

/** The turn (and mirror) that puts a photo upright, from its EXIF orientation; null when it is upright. */
private fun uprightMatrix(orientation: Int): Matrix? = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(270f); postScale(-1f, 1f) }
    else -> null
}

/** A file name that is safe on disk: letters, digits, dots, dashes and underscores; never empty or hidden. */
fun safeFileName(name: String, fallback: String = "file"): String {
    val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(100)
    return clean.ifBlank { fallback }
}

/**
 * Downloads a document's file into the app's cache (`documents/<file id>/<name>`). The cache folder keeps one
 * copy; the system clears it when it needs space.
 */
suspend fun downloadFile(context: Context, api: DocumentsApi, documentId: String, file: DocumentFile): File {
    val body = api.file(documentId, file.id)
    return withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "documents/${safeFileName(file.id)}").apply { mkdirs() }
        val target = File(folder, safeFileName(file.fileName))
        body.use { response -> target.outputStream().use { out -> response.byteStream().copyTo(out) } }
        target
    }
}

/** Opens a downloaded file in another app (a PDF viewer, a gallery); false when no app can show it. */
fun openFile(context: Context, file: File, contentType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, contentType.ifBlank { "application/octet-stream" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(view)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
