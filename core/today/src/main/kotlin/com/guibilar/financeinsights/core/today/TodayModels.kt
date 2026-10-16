package com.guibilar.financeinsights.core.today

import kotlinx.serialization.Serializable

// The answer of GET /api/today (Hestia.Api, module Today; contract Hestia.Modules.Today.Contracts). The server does not
// format amounts or dates: a card text has placeholders such as {difference}, and the card's values give each one.
// An enum value that a newer server sends becomes the field's default (ApiJson coerces it), so it does not fail the page.

/** All cards of the Today screen, from all modules, in display order: first the tiles, then the attention items. */
@Serializable
data class TodayDto(
    /** The day the cards are for, in the server's time zone ("2026-10-13"). */
    val date: String,
    /** When the server made this answer. */
    val generatedAt: String? = null,
    /** Each module that gives cards, with its status. A failed module gives no cards. */
    val modules: List<TodayModule> = emptyList(),
    val cards: List<TodayCard> = emptyList(),
    /** "Your day" (T106): the Calendar of today and of the next days; null when the Calendar did not answer. */
    val agenda: TodayAgenda? = null,
    /** "On this day" (T106): this day in earlier years, newest first. */
    val memories: List<TodayMemory> = emptyList(),
)

/** "Your day" (T106). */
@Serializable
data class TodayAgenda(
    /** The items that touch today: whole days first, then by time. */
    val today: List<TodayAgendaItem> = emptyList(),
    /** The items that start in the next 7 days, soonest first (at most 8). */
    val upcoming: List<TodayAgendaItem> = emptyList(),
    /** False when a module's items did not load. */
    val complete: Boolean = true,
)

/** One thing of "Your day": the fields of a Calendar item. */
@Serializable
data class TodayAgendaItem(
    val id: String,
    val module: String,
    /** "event", "task-due", "birthday", "reach-out", "loan-due", "document-expires", "warranty-ends". */
    val kind: String,
    val title: String,
    val text: String? = null,
    val allDay: Boolean = true,
    val startDate: String,
    val endDate: String = startDate,
    val startsAt: String? = null,
    val endsAt: String? = null,
    /** The web route of the thing; the app maps it (todayDestination). */
    val link: String? = null,
    val eventId: String? = null,
    /** For a task: false while it is open. */
    val done: Boolean? = null,
)

/** Something from this day in an earlier year, for example a journal entry. */
@Serializable
data class TodayMemory(
    val module: String,
    val kind: String,
    val yearsAgo: Int,
    val day: String,
    val text: String,
    val link: String? = null,
)

/** One module on Today, and whether all its cards are there. */
@Serializable
data class TodayModule(val module: String, val status: TodayModuleStatus = TodayModuleStatus.Complete)

@Serializable
enum class TodayModuleStatus {
    /** All cards of the module are there. */
    Complete,

    /** Some data did not load: those tiles have no value and the text "Could not load this". */
    Partial,

    /** The module gave no cards (an error or a timeout). */
    Failed,
}

/** One card on Today: a headline tile or a "Needs attention" item. */
@Serializable
data class TodayCard(
    /** Stable for the same thing, for example "money.budget-over.<budget id>". A list key. */
    val id: String,
    /** The module name, for example "Money". */
    val module: String,
    val type: TodayCardType = TodayCardType.Unknown,
    /** What the card shows inside its module, for example "spent" or "budget-over". The icon follows it. */
    val kind: String = "",
    /** The tile label, or a short name of the attention kind. */
    val title: String = "",
    /** The big value of a tile; null when it is not known ("—"). */
    val value: TodayValue? = null,
    /** The tile hint or the attention sentence, with placeholders; null when there is nothing to say. */
    val text: String? = null,
    val values: Map<String, TodayValue> = emptyMap(),
    val severity: TodaySeverity = TodaySeverity.Neutral,
    /** An arrow next to the text; null for none. */
    val trend: TodayTrend? = null,
    /** A route of the web app, for example "/money/budgets" or "/money/rules#unsorted"; null for none. */
    val link: String? = null,
)

@Serializable
enum class TodayCardType {
    Tile,
    Attention,

    /** A type that a newer server sends: the app does not show it. */
    Unknown,
}

@Serializable
enum class TodaySeverity { Neutral, Good, Warning, Bad }

@Serializable
enum class TodayTrend { Up, Down }

/** One value on a card. [type] says which of [number], [text] and [date] is set. */
@Serializable
data class TodayValue(
    val type: TodayValueType = TodayValueType.Unknown,
    val number: Double? = null,
    val text: String? = null,
    /** A calendar day ("2026-06-30"). */
    val date: String? = null,
) {
    companion object {
        fun money(amount: Double) = TodayValue(TodayValueType.Money, number = amount)
        fun count(count: Int) = TodayValue(TodayValueType.Count, number = count.toDouble())
        fun percent(percent: Double) = TodayValue(TodayValueType.Percent, number = percent)
        fun day(date: String) = TodayValue(TodayValueType.Date, date = date)
        fun plain(text: String) = TodayValue(TodayValueType.Text, text = text)
    }
}

@Serializable
enum class TodayValueType {
    Money,
    Count,
    Percent,
    Date,
    Text,

    /** A type that a newer server sends: its text, if it has one. */
    Unknown,
}
