package com.guibilar.financeinsights.search

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

// The Search API of Hestia.Api (module Search, T17; contracts in Hestia.Modules.Search.Contracts). Days are "yyyy-MM-dd".

/** One thing that matches: a transaction, a document, a task, an item, a person, a journal entry. */
@Serializable
data class SearchHit(
    val id: String,
    val module: String,
    /** "transaction", "document", "task", "item", "person", "entry". The icon follows it. */
    val kind: String = "",
    val title: String = "",
    val text: String? = null,
    val day: String? = null,
    /** Money in is positive. */
    val amount: Double? = null,
    /** The web route of the thing; the app maps it (the one link map). */
    val link: String? = null,
)

/** The hits of one module; [total] is all its matches. */
@Serializable
data class SearchModule(
    val module: String,
    val status: String = "Complete",
    val total: Int = 0,
    val hits: List<SearchHit> = emptyList(),
)

/** The answer of a search: each module in order (Money first), with hits or not. */
@Serializable
data class SearchResult(val query: String, val total: Int = 0, val modules: List<SearchModule> = emptyList())

/** The Search API, under `<server>/api/search`. Make it from the signed-in client: `client.api<SearchApi>()`. */
interface SearchApi {
    /** [q]: 2 to 200 characters. [module]: only that module, with more hits. */
    @GET("search")
    suspend fun search(@Query("q") q: String, @Query("module") module: String? = null): SearchResult
}
