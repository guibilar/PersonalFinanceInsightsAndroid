package com.guibilar.financeinsights.core.calendar

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Calendar API of Hestia.Api (T103), under `<server>/api/calendar`. Make it from the signed-in client:
 * `client.api<CalendarApi>()`. A read-only user can call the GET endpoints only.
 */
interface CalendarApi {
    /** The items from [from] to [to] ("yyyy-MM-dd", both included; at most 400 days). */
    @GET("calendar")
    suspend fun range(@Query("from") from: String, @Query("to") to: String): CalendarView

    @GET("calendar/events/{id}")
    suspend fun event(@Path("id") id: String): CalendarEvent

    @POST("calendar/events")
    suspend fun create(@Body event: CalendarEventRequest): CalendarEvent

    @PUT("calendar/events/{id}")
    suspend fun update(@Path("id") id: String, @Body event: CalendarEventRequest): CalendarEvent

    /** One occurrence of a repeated event does not happen. */
    @POST("calendar/events/{id}/skip")
    suspend fun skip(@Path("id") id: String, @Body skip: CalendarSkip): CalendarEvent

    @DELETE("calendar/events/{id}")
    suspend fun delete(@Path("id") id: String)

    /** Imports an .ics text; with dryRun only says what would change. */
    @POST("calendar/import")
    suspend fun import(@Body request: CalendarImportRequest): CalendarImportResult
}
