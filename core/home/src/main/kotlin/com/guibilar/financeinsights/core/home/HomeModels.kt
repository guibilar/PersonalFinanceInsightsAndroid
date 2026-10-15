package com.guibilar.financeinsights.core.home

import kotlinx.serialization.Serializable

// The models of the Home API of Hestia.Api (module Home, T22; contracts in Hestia.Modules.Home.Contracts/HomeDtos.cs).
// Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601. Amounts are numbers in the one app currency
// (the app shows CAD); the prices are for one item, and `value` is the value of the row (price × quantity).

/** A room of the home, with its item count and the total value of its items. */
@Serializable
data class Room(
    val id: String,
    val name: String = "",
    /** The place of the room in the list: lower first. */
    val sortOrder: Int = 0,
    val itemCount: Int = 0,
    val totalValue: Double = 0.0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** The items of one room in the summary. A null [roomId] (and [name]) is the entry for the items without a room. */
@Serializable
data class RoomCount(
    val roomId: String? = null,
    val name: String? = null,
    val itemCount: Int = 0,
    val totalValue: Double = 0.0,
)

/** The numbers of the Home screen (`GET /api/home/summary`). */
@Serializable
data class HomeSummary(
    /** Today on the server ("yyyy-MM-dd"), in the time zone of the Home module. */
    val date: String? = null,
    /** How many items (rows; not the quantity). */
    val itemCount: Int = 0,
    val totalValue: Double = 0.0,
    /** Each room in list order (also a room with no items), then the items without a room, when there are any. */
    val rooms: List<RoomCount> = emptyList(),
    /** The items whose warranty ends today or in the next 30 days. */
    val warrantiesEnding: Int = 0,
)

/** A link from an item to something outside the Home module, for example its receipt. */
@Serializable
data class ItemLink(
    val id: String,
    /** What the link points to, for example [ItemLinkKinds.DOCUMENT]. */
    val kind: String,
    /** The id of the thing, as text. */
    val targetId: String,
    val createdAt: String? = null,
)

/** The kinds of link that the Home module knows, and the kind that other modules use for an item. */
object ItemLinkKinds {
    /** A document (a receipt, a manual, a warranty, a photo). The target id is the document id. */
    const val DOCUMENT = "Documents.Document"

    /** A finance transaction: the purchase. The target id is the transaction id. */
    const val TRANSACTION = "Money.Transaction"

    /** A task: maintenance. The target id is the task id. */
    const val TASK = "Tasks.Task"

    /** An item, as Documents and Tasks link to it (`POST /api/documents/{id}/links`, `POST /api/tasks/{id}/links`). */
    const val HOME_ITEM = "Home.Item"
}

/** An item in a list. */
@Serializable
data class ItemSummary(
    val id: String,
    val name: String = "",
    val roomId: String? = null,
    val roomName: String? = null,
    /** Free text, for example "Appliance". */
    val category: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    /** How many of this item the home has (1 or more). */
    val quantity: Int = 1,
    val purchasedOn: String? = null,
    /** The price of one item. */
    val purchasePrice: Double? = null,
    /** The value of one item now. */
    val currentValue: Double? = null,
    /** The value of the row: the current value (else the purchase price, else 0) × the quantity. */
    val value: Double = 0.0,
    /** The last day of the warranty. */
    val warrantyUntil: String? = null,
    val notes: String? = null,
    /** In lower case. */
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Oldest first. */
    val links: List<ItemLink> = emptyList(),
)

/** A document of an item: linked from the item, or linked to the item from the document. */
@Serializable
data class ItemDocument(
    val id: String,
    val title: String = "",
    /** The document kind as text, for example "Receipt", "Warranty", "Manual". */
    val kind: String = "",
    val expiresOn: String? = null,
)

/**
 * One maintenance task of one item (`GET /api/home/maintenance`, T89): an open task linked to the item from either
 * side. A task linked to two items comes twice.
 */
@Serializable
data class Maintenance(
    val taskId: String,
    val title: String = "",
    val dueOn: String? = null,
    val repeatEvery: Int? = null,
    /** "Days", "Weeks", "Months" or "Years", for a repeated task. */
    val repeatUnit: String? = null,
    val lastDoneAt: String? = null,
    val itemId: String,
    val itemName: String = "",
    val roomName: String? = null,
)

/** A category in use and how many items have it (`GET /api/home/categories`, T89). */
@Serializable
data class CategoryCount(val name: String, val count: Int = 0)

/** A task of an item (maintenance): linked from the item, or linked to the item from the task. */
@Serializable
data class ItemTask(
    val id: String,
    val title: String = "",
    /** "Open" or "Done". */
    val status: String = "Open",
    /** The next due date. */
    val dueOn: String? = null,
    /** For a repeated task: how many units between two due dates. */
    val repeatEvery: Int? = null,
    /** For a repeated task: "Days", "Weeks", "Months" or "Years". */
    val repeatUnit: String? = null,
    val lastDoneAt: String? = null,
) {
    val isDone get() = status == "Done"
}

/** One item, with its documents and its maintenance tasks (both from both sides of the link). */
@Serializable
data class Item(
    val id: String,
    val name: String = "",
    val roomId: String? = null,
    val roomName: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val quantity: Int = 1,
    val purchasedOn: String? = null,
    val purchasePrice: Double? = null,
    val currentValue: Double? = null,
    val value: Double = 0.0,
    val warrantyUntil: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val links: List<ItemLink> = emptyList(),
    /** By title. */
    val documents: List<ItemDocument> = emptyList(),
    /** By due date. */
    val maintenance: List<ItemTask> = emptyList(),
) {
    /** The same item as a list shows it. */
    val summary
        get() = ItemSummary(
            id, name, roomId, roomName, category, brand, model, serialNumber, quantity, purchasedOn, purchasePrice,
            currentValue, value, warrantyUntil, notes, tags, createdAt, updatedAt, links,
        )

    /** The details as [HomeApi.update] takes them: all of them, so nothing changes by accident. */
    val details
        get() = ItemDetails(
            name, roomId, category, brand, model, serialNumber, quantity, purchasedOn, purchasePrice, currentValue,
            warrantyUntil, notes, tags,
        )
}

/** A page of items (`GET /api/home/items`). */
@Serializable
data class ItemPage(
    val items: List<ItemSummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
)

/** A new room, or the new details of a room (`POST` and `PUT /api/home/rooms`). No [sortOrder]: the place stays. */
@Serializable
data class RoomDetails(val name: String, val sortOrder: Int? = null)

/** A new order of all rooms (`PUT /api/home/rooms/order`): all ids, each one time. */
@Serializable
data class RoomOrder(val roomIds: List<String>)

/** A new item (`POST /api/home/items`). Only the name is needed. */
@Serializable
data class NewItem(
    val name: String,
    val roomId: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val quantity: Int? = null,
    val purchasedOn: String? = null,
    val purchasePrice: Double? = null,
    val currentValue: Double? = null,
    val warrantyUntil: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    /** Links to make at once, for example to the purchase. */
    val links: List<NewItemLink> = emptyList(),
)

/**
 * The details of an item (`PUT /api/home/items/{id}`). All values replace the old ones: a null value becomes empty,
 * and no quantity becomes 1. The links do not change.
 */
@Serializable
data class ItemDetails(
    val name: String? = null,
    val roomId: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val quantity: Int? = null,
    val purchasedOn: String? = null,
    val purchasePrice: Double? = null,
    val currentValue: Double? = null,
    val warrantyUntil: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
)

/** A new link (`POST /api/home/items/{id}/links`). */
@Serializable
data class NewItemLink(val kind: String, val targetId: String) {
    companion object {
        fun document(id: String) = NewItemLink(ItemLinkKinds.DOCUMENT, id)

        fun transaction(id: String) = NewItemLink(ItemLinkKinds.TRANSACTION, id)

        fun task(id: String) = NewItemLink(ItemLinkKinds.TASK, id)
    }
}
