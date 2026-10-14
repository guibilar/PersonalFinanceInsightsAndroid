package com.guibilar.financeinsights.core.documents

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The largest file that the server takes (`Documents:MaxFileSizeMb`). */
const val MAX_FILE_BYTES = 25L * 1024 * 1024

/** The largest upload (all files together) that the server takes (`Documents:MaxUploadSizeMb`). */
const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024

/** The most files in one upload (`Documents:MaxFilesPerDocument`). */
const val MAX_FILES = 10

/** The types that the server takes. It finds the type in the first bytes, never in the name. */
enum class FileType(val mimeType: String, val extension: String) {
    Pdf("application/pdf", "pdf"),
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    Heic("image/heic", "heic"),
    WebP("image/webp", "webp"),
    ;

    val isImage get() = this != Pdf

    companion object {
        private val heicBrands = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1")

        /** The type from the first bytes of a file, as the server finds it; null for a type that it refuses. */
        fun of(bytes: ByteArray): FileType? {
            fun at(offset: Int, text: String) =
                bytes.size >= offset + text.length && text.indices.all { bytes[offset + it] == text[it].code.toByte() }
            return when {
                at(0, "%PDF") -> Pdf
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> Jpeg
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && at(1, "PNG\r\n") -> Png
                at(0, "RIFF") && at(8, "WEBP") -> WebP
                at(4, "ftyp") && bytes.size >= 12 && String(bytes, 8, 4, Charsets.US_ASCII) in heicBrands -> Heic
                else -> null
            }
        }
    }
}

/** A file to upload: its name and its content. */
class UploadFile(val name: String, val bytes: ByteArray) {
    val type: FileType? = FileType.of(bytes)
    val size: Long get() = bytes.size.toLong()
}

/**
 * A new document: the files and the details. [toMultipart] makes the form that `POST /api/documents` reads. A null
 * or blank value is left out, so the server uses its default (the title: the name of the first file; the kind: Other).
 */
data class DocumentUpload(
    val files: List<UploadFile>,
    val title: String? = null,
    val kind: DocumentKind? = null,
    val tags: List<String> = emptyList(),
    /** "yyyy-MM-dd". */
    val issuedOn: String? = null,
    /** "yyyy-MM-dd". */
    val expiresOn: String? = null,
    val notes: String? = null,
) {
    fun toMultipart(): MultipartBody {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
        files.forEach { file ->
            val type = (file.type?.mimeType ?: "application/octet-stream").toMediaType()
            form.addFormDataPart("files", file.name, file.bytes.toRequestBody(type))
        }
        fun field(name: String, value: String?) {
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { form.addFormDataPart(name, it) }
        }
        field("title", title)
        field("kind", kind?.name)
        // One field for each tag: a tag can't hold a comma by mistake.
        tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { form.addFormDataPart("tags", it) }
        field("issuedOn", issuedOn)
        field("expiresOn", expiresOn)
        field("notes", notes)
        return form.build()
    }
}

/** Why the server would refuse these files (size, type or count); null when they can go. */
fun uploadProblem(files: List<UploadFile>): String? {
    if (files.isEmpty()) return "Add a file: a scan, a photo or a PDF."
    if (files.size > MAX_FILES) return "A document can have at most $MAX_FILES files."
    val unknown = files.filter { it.type == null }
    if (unknown.isNotEmpty()) {
        return "Only PDF, JPEG, PNG, HEIC and WebP files can go: ${unknown.joinToString { it.name }}."
    }
    val tooLarge = files.filter { it.size > MAX_FILE_BYTES }
    if (tooLarge.isNotEmpty()) return "Larger than 25 MB: ${tooLarge.joinToString { it.name }}."
    if (files.sumOf { it.size } > MAX_UPLOAD_BYTES) return "All files together are larger than 100 MB."
    return null
}

/** Tags as typed ("kitchen, Warranty"): trimmed, in lower case as the server keeps them, without repeats. */
fun parseTags(text: String): List<String> =
    text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

/** A file size as people read it: "820 KB", "2.4 MB". */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
    else -> {
        val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
        if (tenths % 10 == 0L) "${tenths / 10} MB" else "${tenths / 10}.${tenths % 10} MB"
    }
}
