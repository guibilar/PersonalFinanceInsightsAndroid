package com.guibilar.financeinsights.inbox

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// The Inbox API of Hestia.Api (module Inbox, T18; contracts in Hestia.Modules.Inbox.Contracts). Times are ISO 8601.

/** A quick note. Open while [doneAt] is null. */
@Serializable
data class InboxNote(
    val id: String,
    val text: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val doneAt: String? = null,
    /** Where it went when it was sorted, for example "Task". */
    val sortedTo: String? = null,
    /** "Note" (written) or "Email" (to the Inbox mailbox, T102). */
    val source: String = "Note",
    /** The sender of an email note. */
    val from: String? = null,
    /** The documents made from the attachments of an email. */
    val documentIds: List<String>? = null,
)

@Serializable
data class InboxPage(val items: List<InboxNote> = emptyList(), val totalCount: Int = 0)

@Serializable
data class InboxText(val text: String)

@Serializable
data class InboxDone(val sortedTo: String? = null)

/** The longest note (HestiaBackend, `InboxLimits`). */
const val INBOX_TEXT_LIMIT = 4000

/** The Inbox API, under `<server>/api/inbox`. Make it from the signed-in client: `client.api<InboxApi>()`. */
interface InboxApi {
    /** The open notes, newest first; with [done], the sorted ones, last sorted first. */
    @GET("inbox")
    suspend fun notes(@Query("done") done: Boolean = false, @Query("pageSize") pageSize: Int = 50): InboxPage

    @POST("inbox")
    suspend fun add(@Body text: InboxText): InboxNote

    @PUT("inbox/{id}")
    suspend fun update(@Path("id") id: String, @Body text: InboxText): InboxNote

    @POST("inbox/{id}/done")
    suspend fun done(@Path("id") id: String, @Body done: InboxDone): InboxNote

    @POST("inbox/{id}/reopen")
    suspend fun reopen(@Path("id") id: String): InboxNote

    @DELETE("inbox/{id}")
    suspend fun delete(@Path("id") id: String)
}
