package com.guibilar.financeinsights.core.home

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** The values of the `warranty` filter of [HomeApi.items]. */
object WarrantyFilter {
    /** The warranty ends today or later. */
    const val ACTIVE = "active"

    /** The warranty ends today or in the next 30 days. */
    const val EXPIRING = "expiring"

    /** The warranty ended before today. */
    const val EXPIRED = "expired"
}

/** The `room` filter of [HomeApi.items] for the items without a room. */
const val NO_ROOM = "none"

/**
 * The Home API of Hestia.Api, under `<server>/api/home`. Make it from the signed-in client: `client.api<HomeApi>()`.
 * A read-only user can call the GET endpoints only (also the export).
 */
interface HomeApi {
    /** The item count, the total value, the counts per room, and the warranties that end soon. */
    @GET("home/summary")
    suspend fun summary(): HomeSummary

    /** All rooms, in list order. */
    @GET("home/rooms")
    suspend fun rooms(): List<Room>

    /** Adds a room. 409 for a name that exists (in any letter case). */
    @POST("home/rooms")
    suspend fun createRoom(@Body room: RoomDetails): Room

    /** Renames a room. */
    @PUT("home/rooms/{id}")
    suspend fun updateRoom(@Path("id") id: String, @Body room: RoomDetails): Room

    /** Deletes a room without items. 409 when it has items. */
    @DELETE("home/rooms/{id}")
    suspend fun deleteRoom(@Path("id") id: String)

    /** A new order of all rooms. */
    @PUT("home/rooms/order")
    suspend fun orderRooms(@Body order: RoomOrder): List<Room>

    /** Adds an item, with its links. */
    @POST("home/items")
    suspend fun create(@Body item: NewItem): Item

    /**
     * A page of items, by name (best match first with [q]). [room] is a room id or [NO_ROOM]; [warranty] is a
     * [WarrantyFilter] value. Null leaves a filter out. At most 100 a page.
     */
    @GET("home/items")
    suspend fun items(
        @Query("room") room: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("q") q: String? = null,
        @Query("warranty") warranty: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): ItemPage

    /** The items of one thing, for example a document or a task (from both sides of the link), by name. */
    @GET("home/items/by-link")
    suspend fun byLink(@Query("kind") kind: String, @Query("id") id: String): List<ItemSummary>

    /** One item, with its documents and its maintenance tasks. */
    @GET("home/items/{id}")
    suspend fun item(@Path("id") id: String): Item

    /** New details (all of them). The links stay. */
    @PUT("home/items/{id}")
    suspend fun update(@Path("id") id: String, @Body details: ItemDetails): Item

    /** Deletes the item and its links. */
    @DELETE("home/items/{id}")
    suspend fun delete(@Path("id") id: String)

    /** Links the item to a document, a transaction, or a task. */
    @POST("home/items/{id}/links")
    suspend fun addLink(@Path("id") id: String, @Body link: NewItemLink): ItemLink

    @DELETE("home/items/{id}/links/{linkId}")
    suspend fun removeLink(@Path("id") id: String, @Path("linkId") linkId: String)

    /** The items (all, or the list filters) as a CSV file for an insurance claim: UTF-8 with a BOM. */
    @Streaming
    @GET("home/items/export.csv")
    suspend fun export(
        @Query("room") room: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("q") q: String? = null,
        @Query("warranty") warranty: String? = null,
    ): ResponseBody
}
