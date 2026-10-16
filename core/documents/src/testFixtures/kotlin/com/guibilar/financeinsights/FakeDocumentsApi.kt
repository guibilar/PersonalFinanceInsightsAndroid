package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentDetails
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.DocumentLink
import com.guibilar.financeinsights.core.documents.DocumentLinkCounts
import com.guibilar.financeinsights.core.documents.DocumentLinkKinds
import com.guibilar.financeinsights.core.documents.DocumentPage
import com.guibilar.financeinsights.core.documents.DocumentSummary
import com.guibilar.financeinsights.core.documents.DocumentSuggestions
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.core.documents.FileType
import com.guibilar.financeinsights.core.documents.NewDocumentLink
import com.guibilar.financeinsights.core.documents.OcrStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * The Documents API on a list of documents in memory, as the server keeps them: newest first, the filters and the
 * search applied, uploads and changes kept. Each call is recorded, and any can be made to fail.
 */
class FakeDocumentsApi(documents: List<Document> = DocumentsSamples.all) : DocumentsApi {
    /** The documents, newest first. */
    val documents = documents.toMutableList()

    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()

    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()

    /** The forms of the uploads, as (field name, value or file name) pairs. */
    val uploads = mutableListOf<List<Pair<String, String>>>()

    /** The thumbnails (T61), by file id. A file without one answers 404, as a file the server cannot preview. */
    val thumbnails = mutableMapOf<String, ByteArray>()

    /** The status that [document] answers for a document that is Pending, after that many calls (none: it stays). */
    var ocrDoneAfter: Int? = null

    private var nextId = 100

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    private fun find(id: String) = documents.firstOrNull { it.id == id } ?: throw ApiError.Http(404, "Not found", "No document $id.", null, emptyMap())

    private fun replace(document: Document): Document {
        documents.replaceAll { if (it.id == document.id) document else it }
        return document
    }

    override suspend fun upload(form: MultipartBody): Document = call("upload", form) {
        val fields = form.fields()
        uploads += fields
        fun value(name: String) = fields.lastOrNull { it.first == name }?.second
        val files = form.parts.filter { it.fileName() != null }.mapIndexed { index, part ->
            val bytes = Buffer().also { part.body.writeTo(it) }.readByteArray()
            DocumentFile("f${nextId}-$index", part.fileName()!!, FileType.of(bytes)?.mimeType.orEmpty(), bytes.size.toLong(), "", "2026-10-03T15:00:00Z")
        }
        val document = Document(
            id = "d${nextId++}",
            title = value("title") ?: files.firstOrNull()?.fileName.orEmpty(),
            kind = value("kind")?.let { DocumentKind.valueOf(it) } ?: DocumentKind.Other,
            tags = fields.filter { it.first == "tags" }.map { it.second },
            issuedOn = value("issuedOn"),
            expiresOn = value("expiresOn"),
            notes = value("notes"),
            createdAt = "2026-10-03T15:00:00Z",
            updatedAt = "2026-10-03T15:00:00Z",
            ocrStatus = OcrStatus.Pending,
            files = files,
        )
        documents.add(0, document)
        document
    }

    override suspend fun documents(
        kind: DocumentKind?,
        tag: String?,
        q: String?,
        expiresFrom: String?,
        expiresTo: String?,
        page: Int,
        pageSize: Int,
    ): DocumentPage = call("documents", listOf(kind, tag, q, expiresFrom, expiresTo, page, pageSize)) {
        val matching = documents.filter { d ->
            (kind == null || d.kind == kind) &&
                (tag == null || tag in d.tags) &&
                (q == null || listOfNotNull(d.title, d.notes, d.ocrText, *d.tags.toTypedArray()).any { it.contains(q, ignoreCase = true) }) &&
                (expiresFrom == null || (d.expiresOn != null && d.expiresOn!! >= expiresFrom)) &&
                (expiresTo == null || (d.expiresOn != null && d.expiresOn!! <= expiresTo))
        }
        val items = matching.drop((page - 1) * pageSize).take(pageSize).map { it.summary }
        val pages = if (pageSize <= 0) 0 else (matching.size + pageSize - 1) / pageSize
        DocumentPage(items, page, pageSize, matching.size, pages, page < pages)
    }

    /** How many times each document was read. */
    val reads = mutableMapOf<String, Int>()

    override suspend fun document(id: String): Document = call("document", id) {
        val count = (reads[id] ?: 0) + 1
        reads[id] = count
        val document = find(id)
        val after = ocrDoneAfter
        if (document.ocrStatus == OcrStatus.Pending && after != null && count >= after) {
            replace(document.copy(ocrStatus = OcrStatus.Done, ocrText = "Text found in ${document.title}"))
        } else {
            document
        }
    }

    override suspend fun update(id: String, details: DocumentDetails): Document = call("update", details) {
        val document = find(id)
        replace(
            document.copy(
                title = details.title ?: "",
                kind = details.kind ?: DocumentKind.Other,
                tags = details.tags,
                issuedOn = details.issuedOn,
                expiresOn = details.expiresOn,
                notes = details.notes,
            ),
        )
    }

    override suspend fun delete(id: String) = call("delete", id) {
        find(id)
        documents.removeAll { it.id == id }
        Unit
    }

    /** The content of every file: a few bytes of the right type. */
    override suspend fun file(id: String, fileId: String): ResponseBody = call("file", fileId) {
        val file = find(id).files.first { it.id == fileId }
        val bytes = if (file.isPdf) "%PDF-1.7 sample".toByteArray() else DocumentsSamples.jpegBytes
        bytes.toResponseBody(file.contentType.toMediaType())
    }

    override suspend fun thumbnail(id: String, fileId: String): ResponseBody = call("thumbnail", fileId) {
        find(id).files.firstOrNull { it.id == fileId } ?: throw ApiError.Http(404, "Not found", "No file $fileId.", null, emptyMap())
        val bytes = thumbnails[fileId] ?: throw ApiError.Http(404, "Not found", "No thumbnail for $fileId.", null, emptyMap())
        bytes.toResponseBody("image/jpeg".toMediaType())
    }

    override suspend fun retryOcr(id: String): Document = call("retryOcr", id) {
        replace(find(id).copy(ocrStatus = OcrStatus.Pending, ocrError = null))
    }

    override suspend fun addLink(id: String, link: NewDocumentLink): DocumentLink = call("addLink", link) {
        val document = find(id)
        val created = DocumentLink("l${nextId++}", link.kind, link.targetId, "2026-10-03T15:00:00Z")
        replace(document.copy(links = document.links + created))
        created
    }

    override suspend fun removeLink(id: String, linkId: String) = call("removeLink", linkId) {
        val document = find(id)
        replace(document.copy(links = document.links.filterNot { it.id == linkId }))
        Unit
    }

    override suspend fun byLink(kind: String, id: String): List<DocumentSummary> = call("byLink", kind to id) {
        documents.filter { d -> d.links.any { it.kind == kind && it.targetId == id } }.map { it.summary }
    }

    override suspend fun countByLink(request: DocumentLinkCounts): Map<String, Int> = call("countByLink", request) {
        request.targetIds.associate { id ->
            id.lowercase() to documents.count { d -> d.links.any { it.kind == request.kind && it.targetId.equals(id, ignoreCase = true) } }
        }
    }

    /** The suggestions of each document (T100); none: nothing read. */
    val suggestions = mutableMapOf<String, DocumentSuggestions>()

    override suspend fun suggestions(id: String): DocumentSuggestions = call("suggestions", id) {
        suggestions[id] ?: DocumentSuggestions(id, OcrStatus.Done)
    }

    companion object {
        /** A refusal of a write by the server, for a read-only user. */
        val readOnly = ApiError.Http(403, "Forbidden", null, null, emptyMap())

        /** The kind of the first link of the samples. */
        const val TRANSACTION = DocumentLinkKinds.MONEY_TRANSACTION
    }
}

/** The name of a form part's file; null for a plain field. */
fun MultipartBody.Part.fileName(): String? =
    headers?.get("Content-Disposition")?.let { Regex("filename=\"([^\"]*)\"").find(it)?.groupValues?.get(1) }

/** The fields of a form, in order, as (name, value), a file's value being its file name. */
fun MultipartBody.fields(): List<Pair<String, String>> = parts.map { part ->
    val disposition = part.headers?.get("Content-Disposition").orEmpty()
    val name = Regex("name=\"([^\"]*)\"").find(disposition)?.groupValues?.get(1).orEmpty()
    name to (part.fileName() ?: Buffer().also { part.body.writeTo(it) }.readUtf8())

}
