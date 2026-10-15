package com.guibilar.financeinsights.core.journal

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Journal API of Hestia.Api (T21), under `<server>/api/journal`. Make it from the signed-in client:
 * `client.api<JournalApi>()`. A read-only user can call the GET endpoints only.
 */
interface JournalApi {
    /** Today's entry, the streak, the week, and "On this day". */
    @GET("journal/summary")
    suspend fun summary(): JournalSummary

    /** A page of entries, newest first (with [q]: best match first). Days are "yyyy-MM-dd". */
    @GET("journal/entries")
    suspend fun entries(
        @Query("q") q: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): JournalPage

    /** The entry of one day; 404 when the day has none. */
    @GET("journal/entries/{day}")
    suspend fun entry(@Path("day") day: String): JournalEntry

    /** Writes the whole text of a day: a new entry, or a new text. A day after today is refused (400). */
    @PUT("journal/entries/{day}")
    suspend fun save(@Path("day") day: String, @Body text: JournalText): JournalEntry

    @DELETE("journal/entries/{day}")
    suspend fun delete(@Path("day") day: String)
}
