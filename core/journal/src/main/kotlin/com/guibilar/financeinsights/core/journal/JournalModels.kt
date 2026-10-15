package com.guibilar.financeinsights.core.journal

import kotlinx.serialization.Serializable

// The models of the Journal API of Hestia.Api (module Journal, T21; contracts in
// Hestia.Modules.Journal.Contracts/JournalDtos.cs). Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601.

/** The journal text of one day. A day has at most one entry. */
@Serializable
data class JournalEntry(
    val id: String,
    val day: String,
    val text: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** An entry in a list: the start of its text, on one line. */
@Serializable
data class JournalEntrySummary(
    val id: String,
    val day: String,
    /** At most 200 characters; a longer text ends with "…". */
    val excerpt: String = "",
    /** The length of the whole text. */
    val length: Int = 0,
    val updatedAt: String? = null,
)

/** A day of the week strip. */
@Serializable
data class JournalDay(val day: String, val written: Boolean = false)

/** An entry of the same day in an earlier year. */
@Serializable
data class OnThisDay(val yearsAgo: Int, val entry: JournalEntrySummary)

/** The Journal screen: today's entry, the streak, the week, and "On this day". */
@Serializable
data class JournalSummary(
    /** Today in the time zone of the server. */
    val date: String,
    /** Days in a row with an entry, up to today (or up to yesterday while today has none). */
    val streak: Int = 0,
    /** The last 7 days, oldest first; the last one is today. */
    val week: List<JournalDay> = emptyList(),
    val today: JournalEntry? = null,
    val onThisDay: List<OnThisDay> = emptyList(),
    val entryCount: Int = 0,
)

/** A page of entries. */
@Serializable
data class JournalPage(
    val items: List<JournalEntrySummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalCount: Int = 0,
    val hasNextPage: Boolean = false,
)

/** The body of a save: the whole text of the day. */
@Serializable
data class JournalText(val text: String)

/** The limits of the Journal module (HestiaBackend, `JournalLimits`). */
object JournalLimits {
    const val TEXT = 20_000
    const val SEARCH = 200
}
