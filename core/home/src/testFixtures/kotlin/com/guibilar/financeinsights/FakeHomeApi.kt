package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.home.CategoryCount
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.HomeSummary
import com.guibilar.financeinsights.core.home.Item
import com.guibilar.financeinsights.core.home.ItemDetails
import com.guibilar.financeinsights.core.home.ItemDocument
import com.guibilar.financeinsights.core.home.ItemLink
import com.guibilar.financeinsights.core.home.ItemLinkKinds
import com.guibilar.financeinsights.core.home.ItemPage
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.Maintenance
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.NewItem
import com.guibilar.financeinsights.core.home.NewItemLink
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.core.home.RoomCount
import com.guibilar.financeinsights.core.home.RoomDetails
import com.guibilar.financeinsights.core.home.RoomOrder
import com.guibilar.financeinsights.core.home.WARRANTY_SOON_DAYS
import com.guibilar.financeinsights.core.home.WarrantyFilter
import com.guibilar.financeinsights.core.home.homeDay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.time.LocalDate

/**
 * The Home API on rooms and items in memory, as the server keeps them: rooms in list order with their counts, items by
 * name, the filters applied, and changes and links kept. "Today" is [today]. Each call is recorded, and any can be
 * made to fail.
 */
class FakeHomeApi(
    rooms: List<Room> = HomeSamples.rooms,
    items: List<Item> = HomeSamples.items,
    var today: LocalDate = HomeSamples.TODAY,
) : HomeApi {
    val rooms = rooms.toMutableList()

    /** The items, in the order they were added. */
    val items = items.toMutableList()

    /** The titles of documents that a new link shows in an item's documents, by document id. */
    val documentTitles = mutableMapOf(HomeSamples.WARRANTY_DOCUMENT to "Dishwasher warranty", HomeSamples.PHOTO_DOCUMENT to "Dishwasher photo")

    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()

    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()

    private var nextId = 100

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    private fun conflict(text: String) = ApiError.Http(409, "Conflict", text, null, emptyMap())

    private fun find(id: String) = items.firstOrNull { it.id == id } ?: throw ApiError.Http(404, "Not found", "No item $id.", null, emptyMap())

    private fun replace(item: Item): Item {
        items.replaceAll { if (it.id == item.id) item else it }
        return item
    }

    private fun now() = "${today}T12:00:00Z"

    private fun roomName(id: String?) = id?.let { roomId -> rooms.firstOrNull { it.id == roomId }?.name }

    private fun ordered() = rooms.sortedBy { it.sortOrder }

    private fun value(price: Double?, current: Double?, quantity: Int) = Math.round((current ?: price ?: 0.0) * quantity * 100) / 100.0

    private fun withCounts(room: Room): Room {
        val inRoom = items.filter { it.roomId == room.id }
        return room.copy(itemCount = inRoom.size, totalValue = inRoom.sumOf { it.value })
    }

    /** The open tasks of the items' maintenance, as the server joins them: by due date (no date last), then by title. */
    override suspend fun maintenance(): List<Maintenance> = call("maintenance", null) {
        items.flatMap { item ->
            item.maintenance.filter { it.status == "Open" }.map { task ->
                Maintenance(task.id, task.title, task.dueOn, task.repeatEvery, task.repeatUnit, task.lastDoneAt, item.id, item.name, item.roomName)
            }
        }.sortedWith(compareBy<Maintenance> { it.dueOn == null }.thenBy { it.dueOn }.thenBy { it.title })
    }

    override suspend fun categories(): List<CategoryCount> = call("categories", null) {
        items.mapNotNull { it.category }.groupBy { it.lowercase() }
            .map { (_, names) -> CategoryCount(names.first(), names.size) }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun summary(): HomeSummary = call("summary", null) {
        val counts = ordered().map { withCounts(it) }.map { RoomCount(it.id, it.name, it.itemCount, it.totalValue) }
        val loose = items.filter { it.roomId == null }
        val soon = items.count { item ->
            homeDay(item.warrantyUntil)?.let { it >= today && it <= today.plusDays(WARRANTY_SOON_DAYS) } == true
        }
        HomeSummary(
            date = today.toString(),
            itemCount = items.size,
            totalValue = items.sumOf { it.value },
            rooms = counts + if (loose.isEmpty()) emptyList() else listOf(RoomCount(null, null, loose.size, loose.sumOf { it.value })),
            warrantiesEnding = soon,
        )
    }

    override suspend fun rooms(): List<Room> = call("rooms", null) { ordered().map { withCounts(it) } }

    override suspend fun createRoom(room: RoomDetails): Room = call("createRoom", room) {
        if (rooms.any { it.name.equals(room.name.trim(), ignoreCase = true) }) throw conflict("A room with this name exists.")
        val created = Room("r${nextId++}", room.name.trim(), room.sortOrder ?: ((rooms.maxOfOrNull { it.sortOrder } ?: 0) + 1), createdAt = now(), updatedAt = now())
        rooms += created
        created
    }

    override suspend fun updateRoom(id: String, room: RoomDetails): Room = call("updateRoom", room) {
        val old = rooms.firstOrNull { it.id == id } ?: throw ApiError.Http(404, "Not found", "No room $id.", null, emptyMap())
        if (rooms.any { it.id != id && it.name.equals(room.name.trim(), ignoreCase = true) }) throw conflict("A room with this name exists.")
        val renamed = old.copy(name = room.name.trim(), sortOrder = room.sortOrder ?: old.sortOrder, updatedAt = now())
        rooms.replaceAll { if (it.id == id) renamed else it }
        items.replaceAll { if (it.roomId == id) it.copy(roomName = renamed.name) else it }
        withCounts(renamed)
    }

    override suspend fun deleteRoom(id: String) = call("deleteRoom", id) {
        if (items.any { it.roomId == id }) throw conflict("The room has items. Move or delete them first.")
        rooms.removeAll { it.id == id }
        Unit
    }

    override suspend fun orderRooms(order: RoomOrder): List<Room> = call("orderRooms", order) {
        order.roomIds.forEachIndexed { index, id -> rooms.replaceAll { if (it.id == id) it.copy(sortOrder = index + 1) else it } }
        ordered().map { withCounts(it) }
    }

    override suspend fun create(item: NewItem): Item = call("create", item) {
        val id = "i${nextId++}"
        val quantity = item.quantity ?: 1
        val created = Item(
            id = id,
            name = item.name,
            roomId = item.roomId,
            roomName = roomName(item.roomId),
            category = item.category,
            brand = item.brand,
            model = item.model,
            serialNumber = item.serialNumber,
            quantity = quantity,
            purchasedOn = item.purchasedOn,
            purchasePrice = item.purchasePrice,
            currentValue = item.currentValue,
            value = value(item.purchasePrice, item.currentValue, quantity),
            warrantyUntil = item.warrantyUntil,
            notes = item.notes,
            tags = item.tags,
            createdAt = now(),
            updatedAt = now(),
            links = item.links.mapIndexed { index, link -> ItemLink("k$id-$index", link.kind, link.targetId, now()) },
        )
        items += created
        created
    }

    override suspend fun items(room: String?, category: String?, tag: String?, q: String?, warranty: String?, page: Int, pageSize: Int): ItemPage =
        call("items", listOf(room, category, tag, q, warranty, page, pageSize)) {
            val matching = matching(room, category, tag, q, warranty)
            val shown = matching.drop((page - 1) * pageSize).take(pageSize).map { it.summary }
            val pages = if (pageSize <= 0) 0 else (matching.size + pageSize - 1) / pageSize
            ItemPage(shown, page, pageSize, matching.size, pages, page < pages)
        }

    private fun matching(room: String?, category: String?, tag: String?, q: String?, warranty: String?): List<Item> =
        items.filter { item ->
            val until = homeDay(item.warrantyUntil)
            (room == null || (if (room == NO_ROOM) item.roomId == null else item.roomId == room)) &&
                (category == null || item.category.equals(category, ignoreCase = true)) &&
                (tag == null || tag in item.tags) &&
                (q == null || listOfNotNull(item.name, item.brand, item.model, item.serialNumber, item.category, item.notes, *item.tags.toTypedArray())
                    .any { it.contains(q, ignoreCase = true) }) &&
                when (warranty) {
                    null -> true
                    WarrantyFilter.ACTIVE -> until != null && until >= today
                    WarrantyFilter.EXPIRING -> until != null && until >= today && until <= today.plusDays(WARRANTY_SOON_DAYS)
                    WarrantyFilter.EXPIRED -> until != null && until < today
                    else -> throw ApiError.Http(400, "Bad request", null, null, mapOf("warranty" to listOf("Use active, expiring, or expired.")))
                }
        }.sortedBy { it.name.lowercase() }

    override suspend fun byLink(kind: String, id: String): List<ItemSummary> = call("byLink", kind to id) {
        items.filter { item ->
            item.links.any { it.kind == kind && it.targetId == id } ||
                (kind == ItemLinkKinds.DOCUMENT && item.documents.any { it.id == id }) ||
                (kind == ItemLinkKinds.TASK && item.maintenance.any { it.id == id })
        }.sortedBy { it.name.lowercase() }.map { it.summary }
    }

    override suspend fun item(id: String): Item = call("item", id) { find(id) }

    override suspend fun update(id: String, details: ItemDetails): Item = call("update", details) {
        val item = find(id)
        val quantity = details.quantity ?: 1
        replace(
            item.copy(
                name = details.name.orEmpty(),
                roomId = details.roomId,
                roomName = roomName(details.roomId),
                category = details.category,
                brand = details.brand,
                model = details.model,
                serialNumber = details.serialNumber,
                quantity = quantity,
                purchasedOn = details.purchasedOn,
                purchasePrice = details.purchasePrice,
                currentValue = details.currentValue,
                value = value(details.purchasePrice, details.currentValue, quantity),
                warrantyUntil = details.warrantyUntil,
                notes = details.notes,
                tags = details.tags,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun delete(id: String) = call("delete", id) {
        find(id)
        items.removeAll { it.id == id }
        Unit
    }

    override suspend fun addLink(id: String, link: NewItemLink): ItemLink = call("addLink", link) {
        val item = find(id)
        if (item.links.any { it.kind == link.kind && it.targetId == link.targetId }) throw conflict("The item has this link.")
        val created = ItemLink("k${nextId++}", link.kind, link.targetId, now())
        val documents = if (link.kind == ItemLinkKinds.DOCUMENT && item.documents.none { it.id == link.targetId }) {
            (item.documents + ItemDocument(link.targetId, documentTitles[link.targetId] ?: "Document", "Other")).sortedBy { it.title }
        } else {
            item.documents
        }
        replace(item.copy(links = item.links + created, documents = documents))
        created
    }

    override suspend fun removeLink(id: String, linkId: String) = call("removeLink", linkId) {
        val item = find(id)
        val link = item.links.firstOrNull { it.id == linkId }
        val documents = if (link?.kind == ItemLinkKinds.DOCUMENT) item.documents.filterNot { it.id == link.targetId } else item.documents
        replace(item.copy(links = item.links.filterNot { it.id == linkId }, documents = documents))
        Unit
    }

    override suspend fun export(room: String?, category: String?, tag: String?, q: String?, warranty: String?): ResponseBody =
        call("export", listOf(room, category, tag, q, warranty)) {
            val header = "Room,Name,Category,Brand,Model,Serial number,Quantity,Purchase date,Purchase price,Current value,Warranty until,Documents"
            val rows = matching(room, category, tag, q, warranty).map { item ->
                listOf(
                    item.roomName.orEmpty(), item.name, item.category.orEmpty(), item.brand.orEmpty(), item.model.orEmpty(),
                    item.serialNumber.orEmpty(), item.quantity.toString(), item.purchasedOn.orEmpty(),
                    item.purchasePrice?.let { "%.2f".format(java.util.Locale.ROOT, it) }.orEmpty(),
                    item.currentValue?.let { "%.2f".format(java.util.Locale.ROOT, it) }.orEmpty(), item.warrantyUntil.orEmpty(),
                    item.documents.joinToString("; ") { it.title },
                ).joinToString(",") { field -> if (field.any { it == ',' || it == '"' }) "\"${field.replace("\"", "\"\"")}\"" else field }
            }
            ("﻿" + (listOf(header) + rows).joinToString("\r\n") + "\r\n").toResponseBody("text/csv; charset=utf-8".toMediaType())
        }

    companion object {
        /** A refusal of a write by the server, for a read-only user. */
        val readOnly = ApiError.Http(403, "Forbidden", null, null, emptyMap())
    }
}
