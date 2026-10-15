package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.today.TodayApi
import com.guibilar.financeinsights.core.today.TodayCard
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.TodayDto
import com.guibilar.financeinsights.core.today.TodayModule
import com.guibilar.financeinsights.core.today.TodayModuleStatus
import com.guibilar.financeinsights.core.today.TodaySeverity
import com.guibilar.financeinsights.core.today.TodayTrend
import com.guibilar.financeinsights.core.today.TodayValue
import java.io.IOException

/** The Today API on a fixed answer. Each call is recorded, and it can be made to fail. */
class FakeTodayApi(var answer: TodayDto = TodaySamples.complete) : TodayApi {
    /** The `refresh` flag of each call, in order. */
    val calls = mutableListOf<Boolean>()

    /** Fails as if the server were unreachable. */
    var failing = false

    override suspend fun today(refresh: Boolean?): TodayDto {
        calls += refresh == true
        if (failing) throw IOException("offline")
        return answer
    }
}

/**
 * Answers of `GET /api/today`. [complete] has the same figures as the recorded finance responses of
 * `FakeFinanceApi` on Saturday, October 3, 2026, so Today and the Money home agree in the screenshots.
 */
object TodaySamples {
    const val DATE = "2026-10-03"

    private fun tile(
        id: String,
        kind: String,
        title: String,
        value: TodayValue?,
        text: String?,
        values: Map<String, TodayValue> = emptyMap(),
        severity: TodaySeverity = TodaySeverity.Neutral,
        trend: TodayTrend? = null,
        link: String? = null,
        module: String = "Money",
    ) = TodayCard("${module.lowercase()}.$id", module, TodayCardType.Tile, kind, title, value, text, values, severity, trend, link)

    private fun attention(
        id: String,
        kind: String,
        title: String,
        text: String,
        values: Map<String, TodayValue>,
        severity: TodaySeverity,
        link: String?,
    ) = TodayCard("money.$id", "Money", TodayCardType.Attention, kind, title, null, text, values, severity, null, link)

    val spent = tile(
        "spent-this-month", "spent", "Spent this month", TodayValue.money(186.89),
        "{difference} over a usual month by now", mapOf("difference" to TodayValue.money(42.69)),
        TodaySeverity.Bad, TodayTrend.Up,
    )
    val leftOver = tile(
        "left-over-this-month", "left-over", "Left over this month", TodayValue.money(-186.89),
        "No income yet this month",
    )
    val netWorth = tile(
        "net-worth", "net-worth", "Net worth", TodayValue.money(7750.0),
        "+{change} since last month", mapOf("change" to TodayValue.money(1950.0)),
        TodaySeverity.Good, TodayTrend.Up, "/money/net-worth",
    )
    val budgets = tile(
        "budgets", "budgets", "Budgets", TodayValue.plain("0 of 2 over"), "All within limits",
        severity = TodaySeverity.Good, link = "/money/budgets",
    )
    val priceChanged = attention(
        "price-changed.NETFLIX", "price-changed", "Price change", "{payment} went up from {from} to {to}",
        mapOf("payment" to TodayValue.plain("NETFLIX"), "from" to TodayValue.money(16.49), "to" to TodayValue.money(18.99)),
        TodaySeverity.Warning, "/money/subscriptions",
    )
    val unsorted = attention(
        "unsorted", "unsorted", "Unsorted transactions",
        "{count} transactions are in Other because no rule names their merchants",
        mapOf("count" to TodayValue.count(4)), TodaySeverity.Warning, "/money/rules#unsorted",
    )
    val budgetOver = attention(
        "budget-over.b1", "budget-over", "Budget over its limit", "{budget} is over its limit ({used} used)",
        mapOf("budget" to TodayValue.plain("Groceries"), "used" to TodayValue.percent(112.0)),
        TodaySeverity.Bad, "/money/budgets",
    )

    /** All finance reports loaded. */
    val complete = TodayDto(
        date = DATE,
        generatedAt = "2026-10-03T15:00:00Z",
        modules = listOf(TodayModule("Money", TodayModuleStatus.Complete)),
        cards = listOf(spent, leftOver, netWorth, budgets, priceChanged, unsorted),
    )

    /** The net worth report did not load: its tile has no value, and the module is Partial. */
    val partial = complete.copy(
        modules = listOf(TodayModule("Money", TodayModuleStatus.Partial)),
        cards = complete.cards.map {
            if (it.id == netWorth.id) {
                it.copy(value = null, text = "Could not load this", values = emptyMap(), severity = TodaySeverity.Neutral, trend = null)
            } else {
                it
            }
        },
    )

    /** Finance was down: the Money module gave no cards. */
    val failed = complete.copy(modules = listOf(TodayModule("Money", TodayModuleStatus.Failed)), cards = emptyList())

    /** Money and a second module (Documents) with a tile of its own. */
    /**
     * T81: the server's own alerts (module System, no link) and a People alert, next to the Money cards. System and
     * People have no tiles, so Today shows no section for them; their items are in "Needs attention".
     */
    val withSystem = complete.copy(
        modules = listOf(
            TodayModule("System", TodayModuleStatus.Complete),
            TodayModule("Money", TodayModuleStatus.Complete),
            TodayModule("People", TodayModuleStatus.Complete),
        ),
        cards = complete.cards +
            TodayCard("system.broker-down", "System", TodayCardType.Attention, "broker-down", "No event broker",
                null, "No event broker since {since}", mapOf("since" to TodayValue.day("2026-10-02")), TodaySeverity.Warning, null, null) +
            TodayCard("people.reach-out.p1", "People", TodayCardType.Attention, "reach-out", "Time to reach out",
                null, "Reach out to {person}: last contact on {date}",
                mapOf("person" to TodayValue.plain("Marco Silva"), "date" to TodayValue.day("2026-08-15")),
                TodaySeverity.Neutral, null, "/people/p1"),
    )

    val twoModules = complete.copy(
        modules = complete.modules + TodayModule("Documents", TodayModuleStatus.Complete),
        cards = listOf(spent, leftOver, netWorth, budgets) +
            tile("expiring", "expiring", "Expiring soon", TodayValue.count(2), "Next on {day}", mapOf("day" to TodayValue.day("2026-10-20")), module = "Documents", link = "/documents") +
            listOf(budgetOver, priceChanged, unsorted),
    )

    /** Money and Tasks (T86): the Tasks tile as the server makes it, 3 due today and 2 overdue. */
    val withTasks = complete.copy(
        modules = complete.modules + TodayModule("Tasks", TodayModuleStatus.Complete),
        cards = listOf(spent, leftOver, netWorth, budgets) +
            tile(
                "due-today",
                "due-today",
                "Tasks",
                TodayValue.count(3),
                "{overdue} overdue",
                mapOf("overdue" to TodayValue.count(2)),
                severity = TodaySeverity.Warning,
                module = "Tasks",
                link = "/tasks",
            ) +
            listOf(budgetOver, priceChanged, unsorted),
    )
}
