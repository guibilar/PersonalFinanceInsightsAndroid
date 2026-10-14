package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.today.AlertDto
import com.guibilar.financeinsights.core.today.AlertModule
import com.guibilar.financeinsights.core.today.AlertsApi
import com.guibilar.financeinsights.core.today.AlertsDto
import com.guibilar.financeinsights.core.today.TodayCard
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.TodayModuleStatus
import com.guibilar.financeinsights.core.today.TodaySeverity
import com.guibilar.financeinsights.core.today.TodayValue
import java.io.IOException

/** The Alerts API on a fixed answer. Each call is recorded, and it can be made to fail. */
class FakeAlertsApi(var answer: AlertsDto = AlertSamples.complete) : AlertsApi {
    /** The `refresh` flag of each call, in order. */
    val calls = mutableListOf<Boolean>()

    /** Fails as if the server were unreachable. */
    var failing = false

    override suspend fun alerts(refresh: Boolean?): AlertsDto {
        calls += refresh == true
        if (failing) throw IOException("offline")
        return answer
    }
}

/**
 * Answers of `GET /api/alerts`. The Money items are the attention cards of [TodaySamples], so for the same data the
 * Money pages and Today show the same items.
 */
object AlertSamples {
    /** The alert of a Today attention card, as the server makes both from one stored alert. */
    fun TodayCard.asAlert(raisedAt: String = "2026-10-01T12:00:00Z"): AlertDto {
        check(type == TodayCardType.Attention) { "$id is not an attention card" }
        return AlertDto(
            id = id, alertId = "alert-$id", module = module, kind = kind, title = title, text = text, values = values,
            severity = severity, link = link, raisedAt = raisedAt, updatedAt = raisedAt,
        )
    }

    private val money = AlertModule("Money", order = 0)
    private val documents = AlertModule("Documents", order = 1)

    /** A document that expires soon: an alert of another module, which the Money pages leave out. */
    val documentExpiring = AlertDto(
        id = "documents.expiring.d1", alertId = "alert-d1", module = "Documents", kind = "expiring", title = "Expiring soon",
        text = "{title} expires on {day}", values = mapOf("title" to TodayValue.plain("Lease"), "day" to TodayValue.day("2026-10-20")),
        severity = TodaySeverity.Warning, link = "/documents/d1", raisedAt = "2026-10-02T08:00:00Z", updatedAt = "2026-10-02T08:00:00Z",
    )

    /** The Money items of [TodaySamples.complete], and a document. */
    val complete = AlertsDto(
        checkedAt = "2026-10-03T15:00:00Z",
        modules = listOf(money, documents),
        alerts = listOf(TodaySamples.priceChanged.asAlert(), documentExpiring, TodaySamples.unsorted.asAlert()),
    )

    /** All Money items: a budget over its limit (Bad, so first), a price change, unsorted transactions, and an import. */
    val many = complete.copy(
        alerts = listOf(
            TodaySamples.budgetOver.asAlert(),
            TodaySamples.priceChanged.asAlert(),
            documentExpiring,
            TodaySamples.unsorted.asAlert(),
            AlertDto(
                id = "money.statements-not-imported", alertId = "alert-import", module = "Money", kind = "statements-not-imported",
                title = "Statements to import", text = "No statements imported in {days} days",
                values = mapOf("days" to TodayValue.count(9)), severity = TodaySeverity.Warning,
                link = "/money/transactions#import", raisedAt = "2026-09-30T08:00:00Z", updatedAt = "2026-09-30T08:00:00Z",
            ),
        ),
    )

    /** Nothing needs attention in Money; a document still does. */
    val noMoney = complete.copy(alerts = listOf(documentExpiring))

    /** Some finance reports did not load in the last check: the stored Money alerts are shown. */
    val partial = complete.copy(modules = listOf(money.copy(status = TodayModuleStatus.Partial), documents))

    /** No finance check worked since the server started, and no Money alert is stored. */
    val failed = complete.copy(
        modules = listOf(money.copy(status = TodayModuleStatus.Failed), documents),
        alerts = listOf(documentExpiring),
    )
}
