package com.guibilar.financeinsights.home.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// The files of the Home module on the phone: a new camera photo and the export for insurance, both in the cache
// folder "home/", which the system clears when it needs space.

/** The provider of the Home files. A class of its own, so the app's manifest can hold it next to the Documents one. */
class HomeFileProvider : FileProvider()

private fun authority(context: Context) = "${context.packageName}.home"

private fun folder(context: Context) = File(context.cacheDir, "home").apply { mkdirs() }

private val photoName = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.US)

/** A new, empty file for the camera app to write a photo to, and its content address. */
fun newCameraFile(context: Context): Pair<File, Uri> {
    val file = File(folder(context), "photo-${LocalDateTime.now().format(photoName)}.jpg")
    return file to FileProvider.getUriForFile(context, authority(context), file)
}

/**
 * Saves the export as [name] in the cache and opens the share sheet for it (email, Drive, a messenger). False when it
 * could not: no app can take it, or the file could not be written.
 */
fun shareCsv(context: Context, name: String, bytes: ByteArray): Boolean = runCatching {
    val file = File(folder(context), name)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, authority(context), file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Home inventory")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share the list for insurance").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)
