package com.guibilar.financeinsights.core.today

// The rules of the alert notifications (T68), without Android: which alerts to notify after a poll of GET /api/alerts,
// and which notifications to remove. The app keeps the state and shows the notifications.

/** The alerts that the phone saw in its last poll, and for which account ("server|username"). */
data class SeenAlerts(val owner: String, val alertIds: Set<String>)

/** A notification that the phone shows now for one alert. */
data class ShownAlert(val alertId: String, val module: String)

/** What to do after one poll. */
data class AlertNotificationPlan(
    /** New alerts to notify, one notification each. Empty when [summary] has them. */
    val notify: List<AlertDto>,
    /** More than [MAX_SINGLE_ALERT_NOTIFICATIONS] new alerts: one notification for all of them. Empty otherwise. */
    val summary: List<AlertDto>,
    /** The alert ids of shown notifications to remove: the alert is resolved, or its module is off. */
    val remove: Set<String>,
    /** True when no alert of the shown summary is open any more: remove the summary. */
    val removeSummary: Boolean,
    /** The state to keep for the next poll. */
    val seen: SeenAlerts,
)

/** Up to this many new alerts get a notification each; more get one summary. */
const val MAX_SINGLE_ALERT_NOTIFICATIONS = 3

/**
 * The plan after a successful poll that returned [alerts].
 *
 * - The first poll (no [before], or [before] for another account) only keeps the open alert ids: it notifies nothing.
 * - Else each alert whose `alertId` is not in [before] is new. A new alert of a module that [notifies] refuses is
 *   kept as seen, so it does not come later when the module is turned on.
 * - A shown notification goes away when its alert is not open any more, or when its module is off.
 * - An alert with no `alertId` (a server before T15) cannot be followed, so it is left out.
 */
fun planAlertNotifications(
    alerts: List<AlertDto>,
    before: SeenAlerts?,
    owner: String,
    notifies: (module: String) -> Boolean,
    shown: Collection<ShownAlert> = emptyList(),
    shownSummary: Set<String> = emptySet(),
): AlertNotificationPlan {
    val open = alerts.filter { it.alertId.isNotBlank() }
    val openIds = open.mapTo(linkedSetOf()) { it.alertId }
    val first = before == null || before.owner != owner
    val new = if (first) emptyList() else open.filter { it.alertId !in before!!.alertIds && notifies(it.module) }
    val single = new.size <= MAX_SINGLE_ALERT_NOTIFICATIONS
    return AlertNotificationPlan(
        notify = if (single) new else emptyList(),
        summary = if (single) emptyList() else new,
        remove = shown.filter { it.alertId !in openIds || !notifies(it.module) }.mapTo(linkedSetOf()) { it.alertId },
        removeSummary = shownSummary.isNotEmpty() && shownSummary.none { it in openIds },
        seen = SeenAlerts(owner, openIds),
    )
}
