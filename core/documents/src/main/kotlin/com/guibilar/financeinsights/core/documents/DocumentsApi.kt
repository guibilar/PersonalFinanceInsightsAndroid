package com.guibilar.financeinsights.core.documents

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The Documents API of Hestia.Api, under `<server>/api/documents`. Make it from the signed-in client:
 * `client.api<DocumentsApi>()`. A read-only user can call the GET endpoints only.
 */
interface DocumentsApi {
    /**
     * Uploads a document: one or more files and its details. Make the body with [DocumentUpload.toMultipart]. The
     * answer comes at once, with [OcrStatus.Pending]: the OCR runs in the background.
     */
    @POST("documents")
    suspend fun upload(@Body form: MultipartBody): Document

    /**
     * A page of documents, newest first (best match first with [q]). [expiresFrom] and [expiresTo] are days
     * ("yyyy-MM-dd"), both included. Null leaves a filter out.
     */
    @GET("documents")
    suspend fun documents(
        @Query("kind") kind: DocumentKind? = null,
        @Query("tag") tag: String? = null,
        @Query("q") q: String? = null,
        @Query("expiresFrom") expiresFrom: String? = null,
        @Query("expiresTo") expiresTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): DocumentPage

    /** One document, with its OCR text. */
    @GET("documents/{id}")
    suspend fun document(@Path("id") id: String): Document

    /** New details (all of them). The files stay. */
    @PUT("documents/{id}")
    suspend fun update(@Path("id") id: String, @Body details: DocumentDetails): Document

    /** Deletes the document, its links, and its files. */
    @DELETE("documents/{id}")
    suspend fun delete(@Path("id") id: String)

    /** The content of one file. */
    @Streaming
    @GET("documents/{id}/files/{fileId}")
    suspend fun file(@Path("id") id: String, @Path("fileId") fileId: String): ResponseBody

    /**
     * A small JPEG preview of one file (T61): the first page of a PDF, or the photo, at most 480 pixels on its longest
     * side. 404 when the server cannot make one: show the icon of the type then.
     */
    @GET("documents/{id}/files/{fileId}/thumbnail")
    suspend fun thumbnail(@Path("id") id: String, @Path("fileId") fileId: String): ResponseBody

    /** Runs the OCR again, in the background ([OcrStatus.Pending]). */
    @POST("documents/{id}/ocr")
    suspend fun retryOcr(@Path("id") id: String): Document

    /** Links the document to something outside the module, for example a transaction. */
    @POST("documents/{id}/links")
    suspend fun addLink(@Path("id") id: String, @Body link: NewDocumentLink): DocumentLink

    @DELETE("documents/{id}/links/{linkId}")
    suspend fun removeLink(@Path("id") id: String, @Path("linkId") linkId: String)

    /** The documents linked to one thing, for example a transaction. */
    @GET("documents/by-link")
    suspend fun byLink(@Query("kind") kind: String, @Query("id") id: String): List<DocumentSummary>

    /**
     * How many documents link to each of some things (T72), by id in its stored form (lower case), 0 included. At most
     * [DocumentLinkCounts.MAX_TARGETS] ids. A read-only query: the read-only user can call it.
     */
    @POST("documents/by-link/counts")
    suspend fun countByLink(@Body request: DocumentLinkCounts): Map<String, Int>
}
