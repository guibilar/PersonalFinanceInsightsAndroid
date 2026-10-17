package com.guibilar.financeinsights.core.home

import com.guibilar.financeinsights.core.logic.formatDay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

// The rules of the Home screens, without Android: the warranty state, the short labels, and the item form. The same
// rules as the server (T22) and the web app (T87).

/** The days of "ends soon": the alerts, `warranty=expiring`, and the summary (Home:WarrantySoonDays). */
const val WARRANTY_SOON_DAYS = 30L

/** The longest item name that the server takes. */
const val MAX_ITEM_NAME = 200

/** The longest room name that the server takes. */
const val MAX_ROOM_NAME = 100

/** The largest amount that the server takes (`numeric(12,2)`). */
val MAX_AMOUNT = BigDecimal("9999999999.99")

/** The largest quantity that the form takes. */
const val MAX_QUANTITY = 100_000

/** What the app calls the items without a room. */
const val NO_ROOM_NAME = "No room"

/** A day from the API ("2026-10-03"); null for none or a value that is not a day. */
fun homeDay(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** The warranty of an item on one day. */
sealed interface Warranty {
    /** No warranty date. */
    data object None : Warranty

    /** It ends after the "soon" days. */
    data class Active(val until: LocalDate) : Warranty

    /** It ends today or in the next [WARRANTY_SOON_DAYS] days; [days] is 0 for today. */
    data class EndsSoon(val until: LocalDate, val days: Long) : Warranty

    /** It ended before today. */
    data class Ended(val until: LocalDate) : Warranty
}

/** The warranty state of an item whose warranty ends on [warrantyUntil], on [today]. */
fun warrantyOf(warrantyUntil: String?, today: LocalDate): Warranty {
    val until = homeDay(warrantyUntil) ?: return Warranty.None
    val days = ChronoUnit.DAYS.between(today, until)
    return when {
        days < 0 -> Warranty.Ended(until)
        days <= WARRANTY_SOON_DAYS -> Warranty.EndsSoon(until, days)
        else -> Warranty.Active(until)
    }
}

/**
 * The warranty as a line of the item page: "Under warranty until Mar 1, 2027", "Warranty ends in 17 days",
 * "Warranty ends today", "Warranty ended Aug 1, 2026". Null without a warranty date.
 */
fun warrantyText(warranty: Warranty): String? = when (warranty) {
    Warranty.None -> null
    is Warranty.Active -> "Under warranty until ${formatDay(warranty.until)}"
    is Warranty.EndsSoon -> when (warranty.days) {
        0L -> "Warranty ends today"
        1L -> "Warranty ends tomorrow"
        else -> "Warranty ends in ${warranty.days} days"
    }
    is Warranty.Ended -> "Warranty ended ${formatDay(warranty.until)}"
}

/**
 * The warranty in a list, only when it needs a look: "Warranty ends in 17 days", "Warranty ends today", "Warranty
 * ended". Else null.
 */
fun warrantyBadge(warranty: Warranty): String? = when (warranty) {
    Warranty.None, is Warranty.Active -> null
    is Warranty.EndsSoon -> warrantyText(warranty)
    is Warranty.Ended -> "Warranty ended"
}

/** "1 item", "38 items". */
fun itemsText(count: Int): String = if (count == 1) "1 item" else "$count items"

/** The name of a room, or "No room" for the items without one. */
fun roomLabel(name: String?): String = name?.takeIf { it.isNotBlank() } ?: NO_ROOM_NAME

/** The second line of an item in a list: "Bosch SHX78 · Appliance", the room when [withRoom]. */
fun itemSubtitle(item: ItemSummary, withRoom: Boolean = false): String = listOfNotNull(
    if (withRoom) roomLabel(item.roomName) else null,
    listOfNotNull(item.brand, item.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
    item.category?.takeIf { it.isNotBlank() },
    if (item.quantity > 1) "× ${item.quantity}" else null,
).joinToString(" · ")

/**
 * The days until a maintenance job is due, short, as on the Home screen: "Today", "1 day", "22 days", "3 days late".
 * Null without a due date.
 */
fun daysLeftText(dueOn: String?, today: LocalDate): String? {
    val due = homeDay(dueOn) ?: return null
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days == 0L -> "Today"
        days == 1L -> "1 day"
        days > 1 -> "$days days"
        days == -1L -> "1 day late"
        else -> "${-days} days late"
    }
}

/** The repeat of a maintenance task in words: "Every month", "Every 12 months". Null for a task without a repeat. */
fun repeatText(every: Int?, unit: String?): String? {
    if (every == null || unit == null) return null
    val (one, many) = when (unit) {
        "Days" -> "day" to "days"
        "Weeks" -> "week" to "weeks"
        "Months" -> "month" to "months"
        "Years" -> "year" to "years"
        else -> return null
    }
    return if (every == 1) "Every $one" else "Every $every $many"
}

/**
 * An amount as typed ("899.99", "$1,299", " 12,50 "): a number of 0 or more, rounded to cents as the server does.
 * Null for an empty field. [AmountError] when it is not an amount the server takes.
 */
fun parseAmount(text: String): Double? {
    var clean = text.trim().removePrefix("$").replace(Regex("[\\s\u00A0\u202F]"), "")
    if (clean.isEmpty()) return null
    // "12,50" (a comma for the cents) and "1,299.99" (a comma for the thousands) both read as people mean them, and so
    // does "1.299,99": a comma after the last point is the decimal one, and the points group the thousands.
    clean = when {
        clean.contains('.') && clean.lastIndexOf(',') > clean.lastIndexOf('.') -> clean.replace(".", "").replace(",", ".")
        clean.contains('.') || !Regex(",\\d{1,2}$").containsMatchIn(clean) -> clean.replace(",", "")
        else -> clean.replace(",", ".")
    }
    val amount = clean.toBigDecimalOrNull() ?: throw AmountError("Enter an amount, for example 899.99.")
    if (amount.signum() < 0) throw AmountError("Enter 0 or more.")
    val cents = amount.setScale(2, RoundingMode.HALF_UP)
    if (cents > MAX_AMOUNT) throw AmountError("The amount is too large.")
    return cents.toDouble()
}

/** Why a typed amount can't be saved. */
class AmountError(message: String) : IllegalArgumentException(message)

/** An amount for a form field: "899.99", "1200". */
fun amountText(amount: Double?): String {
    if (amount == null) return ""
    val cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    return if (cents.stripTrailingZeros().scale() <= 0) cents.toBigInteger().toString() else cents.toPlainString()
}

/** Tags as typed ("Kitchen, appliance") as the server keeps them: lower case, trimmed, once each. */
fun parseItemTags(text: String): List<String> =
    text.split(',').map { it.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()

/** The file name of the export, as the server names it: "home-inventory-2026-10-03.csv". */
fun exportFileName(day: LocalDate): String = "home-inventory-$day.csv"

/** The details of an item as typed in the item form. */
data class ItemDraft(
    val name: String = "",
    val roomId: String? = null,
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val serialNumber: String = "",
    /** As typed, so a field can be empty for a moment. */
    val quantity: String = "1",
    val purchasedOn: LocalDate? = null,
    /** As typed: "899.99". */
    val purchasePrice: String = "",
    val currentValue: String = "",
    val warrantyUntil: LocalDate? = null,
    val notes: String = "",
    /** As typed: "kitchen, appliance". */
    val tags: String = "",
) {
    /** Why the name can't be saved; null when it can. */
    val nameError: String?
        get() = when {
            name.isBlank() -> "Enter a name."
            name.trim().length > MAX_ITEM_NAME -> "A name can have at most $MAX_ITEM_NAME characters."
            else -> null
        }

    val quantityError: String?
        get() = when (quantity.trim().toIntOrNull()) {
            null -> if (quantity.isBlank()) null else "Enter a whole number."
            in 1..MAX_QUANTITY -> null
            else -> "Use 1 to $MAX_QUANTITY."
        }

    val purchasePriceError: String? get() = amountError(purchasePrice)

    val currentValueError: String? get() = amountError(currentValue)

    val canSave get() = nameError == null && quantityError == null && purchasePriceError == null && currentValueError == null

    private fun amountError(text: String): String? = try {
        parseAmount(text)
        null
    } catch (error: AmountError) {
        error.message
    }

    private fun amount(text: String): Double? = runCatching { parseAmount(text) }.getOrNull()

    private fun text(value: String) = value.trim().ifEmpty { null }

    /** The details to send: all of them, empty ones as nothing (an empty quantity is 1). */
    fun toDetails() = ItemDetails(
        name = name.trim(),
        roomId = roomId,
        category = text(category),
        brand = text(brand),
        model = text(model),
        serialNumber = text(serialNumber),
        quantity = quantity.trim().toIntOrNull() ?: 1,
        purchasedOn = purchasedOn?.toString(),
        purchasePrice = amount(purchasePrice),
        currentValue = amount(currentValue),
        warrantyUntil = warrantyUntil?.toString(),
        notes = text(notes),
        tags = parseItemTags(tags),
    )

    /** A new item with these details and [links]. */
    fun toNewItem(links: List<NewItemLink> = emptyList()): NewItem {
        val details = toDetails()
        return NewItem(
            name = details.name.orEmpty(),
            roomId = details.roomId,
            category = details.category,
            brand = details.brand,
            model = details.model,
            serialNumber = details.serialNumber,
            quantity = details.quantity,
            purchasedOn = details.purchasedOn,
            purchasePrice = details.purchasePrice,
            currentValue = details.currentValue,
            warrantyUntil = details.warrantyUntil,
            notes = details.notes,
            tags = details.tags,
            links = links,
        )
    }

    /** The purchase fills the price and the date when they are empty ("Link the purchase"). */
    fun withPurchase(amount: Double, day: LocalDate?): ItemDraft = copy(
        purchasePrice = purchasePrice.ifBlank { amountText(abs(amount)) },
        purchasedOn = purchasedOn ?: day,
    )

    companion object {
        fun of(item: Item) = ItemDraft(
            name = item.name,
            roomId = item.roomId,
            category = item.category.orEmpty(),
            brand = item.brand.orEmpty(),
            model = item.model.orEmpty(),
            serialNumber = item.serialNumber.orEmpty(),
            quantity = item.quantity.toString(),
            purchasedOn = homeDay(item.purchasedOn),
            purchasePrice = amountText(item.purchasePrice),
            currentValue = amountText(item.currentValue),
            warrantyUntil = homeDay(item.warrantyUntil),
            notes = item.notes.orEmpty(),
            tags = item.tags.joinToString(", "),
        )

        /**
         * A new item from a transaction ("Add to inventory" in Money): its description as the name, its amount (as a
         * positive price) and its day.
         */
        fun fromPurchase(description: String, amount: Double?, day: LocalDate?, roomId: String? = null) = ItemDraft(
            name = description.trim().take(MAX_ITEM_NAME),
            roomId = roomId,
            purchasedOn = day,
            purchasePrice = amountText(amount?.let(::abs)),
        )
    }
}

/** Why a room name can't be saved; null when it can. */
fun roomNameError(name: String): String? = when {
    name.isBlank() -> "Enter a name."
    name.trim().length > MAX_ROOM_NAME -> "A name can have at most $MAX_ROOM_NAME characters."
    else -> null
}
