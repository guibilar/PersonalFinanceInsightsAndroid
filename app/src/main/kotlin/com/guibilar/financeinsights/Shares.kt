package com.guibilar.financeinsights

import android.content.Context
import android.net.Uri
import com.guibilar.financeinsights.core.documents.MAX_FILE_BYTES
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.readFile
import kotlinx.coroutines.flow.MutableStateFlow

/** Something shared to Hestia from another app (T101), until the screens take it (after signing in, if need be). */
sealed interface Share {
    /** Text or a link: a note for the Inbox. */
    data class Text(val text: String) : Share

    /** Photos (or other documents): a new document. */
    data class Files(val files: List<UploadFile>) : Share

    /** PDFs: a document to keep, or a statement to import. The person chooses. */
    data class Pdfs(val documents: List<UploadFile>, val statements: List<PickedFile>) : Share

    /** An .ics file: its events, to import into the Calendar. */
    data class Calendar(val ics: String) : Share

    /** A .vcf file: its contacts, to import into People. */
    data class Contacts(val vCard: String) : Share
}

/** What a shared file is, by its type and its name. */
enum class SharedKind { Statement, Pdf, Image, Calendar, Contacts, Other }

fun sharedKind(mimeType: String?, name: String?): SharedKind {
    val type = mimeType?.lowercase().orEmpty()
    val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when {
        type in setOf("text/calendar", "application/ics", "text/x-vcalendar") || ext == "ics" -> SharedKind.Calendar
        type in setOf("text/vcard", "text/x-vcard", "text/directory") || ext == "vcf" -> SharedKind.Contacts
        type in setOf("text/csv", "text/comma-separated-values", "application/csv") || ext == "csv" -> SharedKind.Statement
        type == "application/pdf" || ext == "pdf" -> SharedKind.Pdf
        type.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "heic", "heif", "webp") -> SharedKind.Image
        else -> SharedKind.Other
    }
}

/**
 * A shared file's name and contents; null when it can't be read. Read up to a document file's limit, not a statement's:
 * a photo or a PDF past 5 MB would go cut short, and broken, to Documents. A statement that large is refused as before.
 */
fun readShared(context: Context, uri: Uri): PickedFile? = readFile(context, uri, limit = MAX_FILE_BYTES)

/** The share that waits for the screens. */
object Shares {
    val pending = MutableStateFlow<Share?>(null)
}
