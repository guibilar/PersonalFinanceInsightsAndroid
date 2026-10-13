package com.guibilar.financeinsights.core.documents

import kotlinx.serialization.Serializable

// The models of the Documents API of Hestia.Api (module Documents, T11; contracts in
// Hestia.Modules.Documents.Contracts/DocumentDtos.cs). Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601.
// An enum value that a newer server sends becomes the field's default (ApiJson coerces it), so it does not fail the page.

/** What a document is. The app picks the icon and the filter with it. */
@Serializable
enum class DocumentKind(val label: String, val plural: String) {
    Receipt("Receipt", "Receipts"),
    Warranty("Warranty", "Warranties"),
    Lease("Lease", "Leases"),
    Tax("Tax", "Taxes"),
    Insurance("Insurance", "Insurance"),
    Manual("Manual", "Manuals"),
    Other("Other", "Other"),
}

/** The state of the text recognition (OCR) of a document. */
@Serializable
enum class OcrStatus {
    /** The OCR did not run yet. It runs in the background, soon after the upload. */
    Pending,

    /** The OCR ran on all files. */
    Done,

    /** The OCR failed on one or more files. `ocrError` says why. A retry runs it again. */
    Failed,

    /** No OCR: it was off on the server when the document was added. */
    NotNeeded,

    /** A status that a newer server sends: the app shows nothing for it. */
    Unknown,
}

/** One file of a document. */
@Serializable
data class DocumentFile(
    val id: String,
    /** The name of the file when it was uploaded. */
    val fileName: String = "",
    /** The type that the server found in the content, for example "application/pdf" or "image/jpeg". */
    val contentType: String = "",
    /** The size in bytes. */
    val size: Long = 0,
    val sha256: String = "",
    val addedAt: String? = null,
) {
    val isImage get() = contentType.startsWith("image/")
    val isPdf get() = contentType == "application/pdf"
}

/** A link from a document to something outside the Documents module, for example a finance transaction. */
@Serializable
data class DocumentLink(
    val id: String,
    /** What the link points to, for example [DocumentLinkKinds.MONEY_TRANSACTION]. */
    val kind: String,
    /** The id of the thing, as text. */
    val targetId: String,
    val createdAt: String? = null,
)

/** The kinds of link that the Documents module knows. */
object DocumentLinkKinds {
    /** A finance transaction. The target id is the transaction id. */
    const val MONEY_TRANSACTION = "Money.Transaction"
}

/** A document in a list: everything except the OCR text. */
@Serializable
data class DocumentSummary(
    val id: String,
    val title: String = "",
    val kind: DocumentKind = DocumentKind.Other,
    /** In lower case. */
    val tags: List<String> = emptyList(),
    val issuedOn: String? = null,
    val expiresOn: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.Unknown,
    val files: List<DocumentFile> = emptyList(),
    val links: List<DocumentLink> = emptyList(),
)

/** One document, with its OCR text. */
@Serializable
data class Document(
    val id: String,
    val title: String = "",
    val kind: DocumentKind = DocumentKind.Other,
    val tags: List<String> = emptyList(),
    val issuedOn: String? = null,
    val expiresOn: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.Unknown,
    /** Why the OCR failed, when it failed. */
    val ocrError: String? = null,
    /** The text of the files. Null before the OCR. */
    val ocrText: String? = null,
    val files: List<DocumentFile> = emptyList(),
    val links: List<DocumentLink> = emptyList(),
) {
    /** The same document as a list shows it. */
    val summary
        get() = DocumentSummary(id, title, kind, tags, issuedOn, expiresOn, notes, createdAt, updatedAt, ocrStatus, files, links)

    /** The details as [DocumentsApi.update] takes them: all of them, so nothing changes by accident. */
    val details get() = DocumentDetails(title, kind, tags, issuedOn, expiresOn, notes)

    /** The links to finance transactions. */
    val transactionLinks get() = links.filter { it.kind == DocumentLinkKinds.MONEY_TRANSACTION }
}

/** A page of documents (`GET /api/documents`). */
@Serializable
data class DocumentPage(
    val items: List<DocumentSummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
)

/**
 * The details of a document (`PUT /api/documents/{id}`). All values replace the old ones: a null value becomes empty.
 * The files do not change.
 */
@Serializable
data class DocumentDetails(
    val title: String? = null,
    val kind: DocumentKind? = null,
    val tags: List<String> = emptyList(),
    val issuedOn: String? = null,
    val expiresOn: String? = null,
    val notes: String? = null,
)

/** A new link (`POST /api/documents/{id}/links`). */
@Serializable
data class NewDocumentLink(val kind: String, val targetId: String) {
    companion object {
        fun transaction(id: String) = NewDocumentLink(DocumentLinkKinds.MONEY_TRANSACTION, id)
    }
}
