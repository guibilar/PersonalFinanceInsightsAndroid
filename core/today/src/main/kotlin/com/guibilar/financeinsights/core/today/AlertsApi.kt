package com.guibilar.financeinsights.core.today

import com.guibilar.financeinsights.core.logic.DataChanges
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.atomic.AtomicLong

/**
 * The Alerts API of Hestia.Api (T15): the open "Needs attention" items of all modules. Make it from the signed-in
 * client: `client.api<AlertsApi>()`. The items have the format of the Today attention cards, so the same text filler
 * and link map serve both.
 */
interface AlertsApi {
    /**
     * The open alerts. With [refresh] true, the server checks the rules first (it waits at most 10 seconds). Null
     * leaves the parameter out, and the server answers with the alerts of its last check.
     */
    @GET("alerts")
    suspend fun alerts(@Query("refresh") refresh: Boolean? = null): AlertsDto
}

// The answer of GET /api/alerts (Hestia.Api, module Alerts; contract Hestia.Modules.Alerts.Contracts, AlertList). An enum
// value that a newer server sends becomes the field's default (ApiJson coerces it), so it does not fail the page.

/** All open alerts, most severe first, then newest first. */
@Serializable
data class AlertsDto(
    /** When the last check of all modules finished; null when none did since the server started. */
    val checkedAt: String? = null,
    /** Each module that has alert rules, with the status of its last check. */
    val modules: List<AlertModule> = emptyList(),
    val alerts: List<AlertDto> = emptyList(),
) {
    /**
     * The alerts of one module (for example "Money"), in the server's order, and the status of its last check. A
     * module that the server does not list has no rules there yet: it is complete, with no alerts.
     */
    fun of(module: String): ModuleAlerts = ModuleAlerts(
        status = modules.firstOrNull { it.module == module }?.status ?: TodayModuleStatus.Complete,
        items = alerts.filter { it.module == module },
    )
}

/**
 * One module with alert rules. The status names are the same as on Today: `Partial` when some data did not load in the
 * last check (the stored alerts are shown), `Failed` when no check worked and there are no stored alerts.
 */
@Serializable
data class AlertModule(
    val module: String,
    /** Its place on Today: lower first. */
    val order: Int = 0,
    val status: TodayModuleStatus = TodayModuleStatus.Complete,
)

/** One open alert: the fields of a Today attention card, and when it started and last changed. */
@Serializable
data class AlertDto(
    /** Stable for the same condition, for example "money.budget-over.<budget id>": the Today card id. A list key. */
    val id: String,
    /** This alert. When a condition ends and comes back, the new alert has a new id. */
    val alertId: String = "",
    /** The module name, for example "Money". */
    val module: String,
    /** What the alert is about inside its module, for example "budget-over". The icon follows it. */
    val kind: String = "",
    /** A short name of its kind. */
    val title: String = "",
    /** The sentence, with placeholders such as {budget}. */
    val text: String? = null,
    val values: Map<String, TodayValue> = emptyMap(),
    val severity: TodaySeverity = TodaySeverity.Neutral,
    /** A route of the web app, for example "/money/budgets"; null for none. Use [todayDestination]. */
    val link: String? = null,
    /** When the condition started. */
    val raisedAt: String? = null,
    /** When a check last changed the text, a value or the severity. */
    val updatedAt: String? = null,
)

/** The alerts of one module, and whether its last check was complete. */
data class ModuleAlerts(val status: TodayModuleStatus, val items: List<AlertDto>)

/** The alert's text, filled in with the app's formats (amounts hidden in privacy mode); empty when it has none. */
val AlertDto.filledText: String get() = fillTodayText(text, values)

/**
 * Whether the next alerts load must ask for a fresh check (`refresh=true`): the data changed in this app
 * ([DataChanges]) since the last fresh check. One value for the whole app, because a fresh check on the server serves
 * every screen. The same rule as Today, which asks for new cards after a change.
 */
object AlertFreshness {
    // DataChanges starts at 0, so a new process loads the stored alerts first.
    private val checkedVersion = AtomicLong(0L)

    /** True when the data changed since the last fresh check. */
    val stale: Boolean get() = DataChanges.version != checkedVersion.get()

    /** A fresh check finished for the data at [version] (the [DataChanges.version] read before the call). */
    fun checked(version: Long) {
        checkedVersion.set(version)
    }
}
