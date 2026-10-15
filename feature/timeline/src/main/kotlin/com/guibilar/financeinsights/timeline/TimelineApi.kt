package com.guibilar.financeinsights.timeline

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

// The Timeline API of Hestia.Api (module Timeline, T16; contracts in Hestia.Modules.Timeline.Contracts). Days are
// "yyyy-MM-dd". Times are ISO 8601.

/** One thing that happened: a transaction, a document added, a task done, a journal entry… */
@Serializable
data class TimelineEntry(
    /** Stable and unique, for example "documents.document-added.<id>". */
    val id: String,
    /** The module, for example "Documents". */
    val module: String,
    /** What happened inside the module, for example "document-added". The icon follows it. */
    val kind: String = "",
    val day: String,
    /** When it happened, or null for a thing of a whole day. */
    val at: String? = null,
    val title: String = "",
    val text: String? = null,
    /** Money in is positive, money out negative. */
    val amount: Double? = null,
    /** The web route of the thing; the app maps it (the one link map). */
    val link: String? = null,
)

@Serializable
data class TimelineDay(val day: String, val entries: List<TimelineEntry> = emptyList())

@Serializable
data class TimelineModule(val module: String, val status: String = "Complete")

/**
 * One page: the days from [from] to [to], newest first (only days with entries). [earlier] is the next page's `before`:
 * that page ends on the latest earlier day with an entry. [hasEarlier] is false when no module has anything earlier (an
 * older host leaves it out: true).
 */
@Serializable
data class TimelinePage(
    val today: String,
    val from: String,
    val to: String,
    val earlier: String,
    val days: List<TimelineDay> = emptyList(),
    val modules: List<TimelineModule> = emptyList(),
    val hasEarlier: Boolean = true,
)

/** The Timeline API, under `<server>/api/timeline`. Make it from the signed-in client: `client.api<TimelineApi>()`. */
interface TimelineApi {
    /** One page: it ends the day before [before] (none: today), with [days] days (1 to 31), of one [module] or all. */
    @GET("timeline")
    suspend fun page(
        @Query("before") before: String? = null,
        @Query("days") days: Int = 14,
        @Query("module") module: String? = null,
    ): TimelinePage
}
