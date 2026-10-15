package com.guibilar.financeinsights.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.guibilar.financeinsights.FinanceApp
import com.guibilar.financeinsights.MainActivity
import com.guibilar.financeinsights.R
import com.guibilar.financeinsights.alerts.Alerts
import com.guibilar.financeinsights.core.today.AlertDto
import com.guibilar.financeinsights.core.today.AlertNotificationPlan
import com.guibilar.financeinsights.core.today.AlertsApi
import com.guibilar.financeinsights.core.today.ShownAlert
import com.guibilar.financeinsights.core.today.filledText
import com.guibilar.financeinsights.core.today.planAlertNotifications
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notifications from the server alerts (T68): a periodic job polls `GET /api/alerts` over Tailscale (D4; no push
 * service), and notifies each alert that the phone did not see before. A notification goes away when its alert is
 * resolved. A tap opens the alert's page through the one link map (`todayDestination`), as on Today.
 */
object AlertNotifications {
    /** The channel of all server alerts, in the phone's notification settings. The modules are switched in the app. */
    const val CHANNEL = "alerts"

    /** The intent action of a tap; [EXTRA_LINK] holds the alert's link (empty for Today). */
    const val ACTION_OPEN_ALERT = "com.guibilar.financeinsights.OPEN_ALERT"
    const val EXTRA_LINK = "link"

    private const val WORK = "alert-notifications"
    private const val NOW_WORK = "alert-notifications-now"

    /** Every alert notification has this id; its tag tells the alert apart ("alert|<module>|<alertId>"). */
    private const val NOTIFICATION_ID = 68
    private const val TAG_PREFIX = "alert|"
    private const val SUMMARY_TAG = "alert-summary"
    private const val EXTRA_ALERT_IDS = "com.guibilar.financeinsights.ALERT_IDS"

    /** The default poll interval. */
    const val POLL_MINUTES = 60L

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** [delay]: minutes before the first run. */
    private fun periodic(delay: Long = 0) = PeriodicWorkRequestBuilder<AlertPollWorker>(POLL_MINUTES, TimeUnit.MINUTES)
        .setConstraints(online)
        .setInitialDelay(delay, TimeUnit.MINUTES)
        .build()

    /** Work that outlives a screen: forgetting the seen alerts. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.w(LOG_TAG, "Could not forget the seen alerts", e) },
    )

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Needs attention", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When something needs attention: a budget over its limit, a document that expires soon, and more"
            },
        )
    }

    /** After a settings change: polls while the notifications are on, and stops and forgets when they are off. */
    fun reschedule(context: Context, settings: AlertNotificationSettings) {
        val work = WorkManager.getInstance(context)
        if (settings.enabled.value) {
            // The poll now (below) is the first one; the periodic poll starts one interval later.
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic(delay = POLL_MINUTES))
            // One poll now, as a first poll: it keeps the open alerts as seen, so turning on notifies nothing old.
            work.enqueueUniqueWork(
                NOW_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AlertPollWorker>()
                    .setConstraints(online)
                    .setInputData(workDataOf(AlertPollWorker.FIRST to true))
                    .build(),
            )
        } else {
            work.cancelUniqueWork(WORK)
            work.cancelUniqueWork(NOW_WORK)
            forget(context)
        }
    }

    /**
     * Removes the alert notifications and the seen alerts: after a sign-out, or when the notifications are turned off.
     * The next poll is then a first poll, which notifies nothing.
     */
    fun forget(context: Context) {
        removeAll(context)
        val store = SeenAlertStore.of(context)
        scope.launch { store.clear() }
    }

    /** The least time between two polls that the app asks for itself (T81): on opening, and on a pull to refresh. */
    const val SOON_MINUTES = 15L

    private const val SOON_WORK = "alert-notifications-soon"
    private const val PREFS = "alert-notifications-soon"
    private const val KEY_LAST = "lastSoonPoll"

    /**
     * One poll soon (T81): when the app comes to the front, and after a pull to refresh on Today, so a new alert shows
     * without waiting for the hourly poll. At most once every [SOON_MINUTES] minutes, and only while the notifications
     * are on. It is a normal poll: the seen alerts (T68) stay the same, so nothing is notified twice.
     */
    fun pollSoon(context: Context, settings: AlertNotificationSettings, now: Long = System.currentTimeMillis()) {
        if (!settings.enabled.value) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!dueForSoonPoll(prefs.getLong(KEY_LAST, 0L), now)) return
        prefs.edit { putLong(KEY_LAST, now) }
        WorkManager.getInstance(context).enqueueUniqueWork(
            SOON_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AlertPollWorker>().setConstraints(online).build(),
        )
    }

    /** Whether a poll asked for at [now] may run, after the last one at [last] (both in milliseconds). */
    fun dueForSoonPoll(last: Long, now: Long): Boolean = now - last >= TimeUnit.MINUTES.toMillis(SOON_MINUTES) || now < last

    /** On start: keeps the poll queued while the notifications are on. */
    fun keepScheduled(context: Context, settings: AlertNotificationSettings) {
        if (!settings.enabled.value) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, periodic())
    }

    /** The alert notifications on screen now: one per alert, and the alert ids of the summary (empty for none). */
    fun shown(context: Context): Pair<List<ShownAlert>, Set<String>> {
        val single = mutableListOf<ShownAlert>()
        var summary = emptySet<String>()
        val active = runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications }.getOrNull()
        active.orEmpty().filter { it.id == NOTIFICATION_ID }.forEach { shown ->
            val tag = shown.tag ?: return@forEach
            when {
                tag == SUMMARY_TAG -> summary = shown.notification.extras.getStringArray(EXTRA_ALERT_IDS).orEmpty().toSet()
                tag.startsWith(TAG_PREFIX) -> {
                    val parts = tag.removePrefix(TAG_PREFIX).split('|', limit = 2)
                    if (parts.size == 2) single += ShownAlert(alertId = parts[1], module = parts[0])
                }
            }
        }
        return single to summary
    }

    /** Shows and removes the notifications of [plan]. */
    fun apply(context: Context, plan: AlertNotificationPlan, shown: List<ShownAlert>, private: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        shown.filter { it.alertId in plan.remove }.forEach { manager.cancel(tag(it.module, it.alertId), NOTIFICATION_ID) }
        if (plan.removeSummary) manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
        plan.notify.forEach { show(context, it, private) }
        if (plan.summary.isNotEmpty()) showSummary(context, plan.summary, private)
    }

    /** Removes the alert notifications of [module]: it was just turned off. */
    fun removeModule(context: Context, module: String) {
        val manager = NotificationManagerCompat.from(context)
        shown(context).first.filter { it.module == module }.forEach { manager.cancel(tag(it.module, it.alertId), NOTIFICATION_ID) }
    }

    /** Removes every alert notification. */
    fun removeAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val (single, summary) = shown(context)
        single.forEach { manager.cancel(tag(it.module, it.alertId), NOTIFICATION_ID) }
        if (summary.isNotEmpty()) manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
    }

    private fun tag(module: String, alertId: String) = "$TAG_PREFIX$module|$alertId"

    /** A tap opens [link] (through `todayDestination`); null opens Today. */
    private fun open(context: Context, key: String, link: String?): PendingIntent = PendingIntent.getActivity(
        context,
        key.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_ALERT)
            // The data makes each alert's intent a different one, so one tap does not open another alert's page.
            .setData(Uri.fromParts("hestia-alert", key, null))
            .putExtra(EXTRA_LINK, link.orEmpty())
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun show(context: Context, alert: AlertDto, private: Boolean) {
        // The text has the app's formats: with "hide amounts" on, amounts read as hidden here too.
        val text = alert.filledText
        val title = alert.title.ifBlank { alert.module }
        val open = open(context, alert.alertId, alert.link)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text.ifEmpty { null })
            .setSubText(alert.module)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply {
                raisedAt(alert)?.let { setWhen(it).setShowWhen(true) }
                // With the app lock on, the lock screen shows the module and the kind of alert, not the text.
                if (private) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat_alert)
                            .setContentTitle(title)
                            .setSubText(alert.module)
                            .setContentIntent(open)
                            .build(),
                    )
                }
            }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(tag(alert.module, alert.alertId), NOTIFICATION_ID, notification)
    }

    private fun showSummary(context: Context, alerts: List<AlertDto>, private: Boolean) {
        val title = "${alerts.size} new items need attention"
        val open = open(context, SUMMARY_TAG, null)
        val style = NotificationCompat.InboxStyle().also { style ->
            alerts.take(SUMMARY_LINES).forEach { style.addLine(it.filledText.ifEmpty { it.title }) }
            if (alerts.size > SUMMARY_LINES) style.setSummaryText("and ${alerts.size - SUMMARY_LINES} more")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(alerts.joinToString(", ") { it.title.ifBlank { it.module } })
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addExtras(Bundle().apply { putStringArray(EXTRA_ALERT_IDS, alerts.map { it.alertId }.toTypedArray()) })
            .apply {
                if (private) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat_alert)
                            .setContentTitle(title)
                            .setContentIntent(open)
                            .build(),
                    )
                }
            }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(SUMMARY_TAG, NOTIFICATION_ID, notification)
    }

    private fun raisedAt(alert: AlertDto): Long? =
        alert.raisedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private const val SUMMARY_LINES = 5
    private const val LOG_TAG = "AlertNotifications"
}

/** What one poll did. */
sealed interface AlertPollResult {
    /** Nothing done: the notifications are off, not allowed, or nobody is signed in. Nothing stored changed. */
    data object Skipped : AlertPollResult

    /** The call failed (Tailscale off, server down, session ended). Nothing stored changed; the next poll tries again. */
    data class Failed(val error: Throwable) : AlertPollResult

    data class Done(val plan: AlertNotificationPlan) : AlertPollResult
}

/** One poll of the alerts: the parts of [AlertPollWorker] that tests call directly. */
class AlertPoller(
    private val context: Context,
    private val settings: AlertNotificationSettings,
    private val store: SeenAlertStore,
    /** The app lock is on: the lock screen hides the text of the notifications. */
    private val private: () -> Boolean = { false },
    private val canNotify: () -> Boolean = { Alerts.canNotify(context) },
) {
    /**
     * Polls with [api] (null when signed out) for the account [owner] ("server|username"). With [first], the poll
     * only keeps the open alerts as seen, as after install or sign-in.
     */
    suspend fun poll(api: AlertsApi?, owner: String, first: Boolean = false): AlertPollResult {
        // Without permission to notify, nothing is kept as seen: new alerts show once it is given.
        if (!settings.enabled.value || !canNotify() || api == null) return AlertPollResult.Skipped
        val answer = try {
            // The server's stored alerts: its rules run in the background every 15 minutes.
            api.alerts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Normal when Tailscale is off (D4). A 401 means the session ended: no sign-in from the background.
            Log.i(LOG_TAG, "The alerts poll failed: ${e.message}")
            return AlertPollResult.Failed(e)
        }
        val before = if (first) null else store.read()
        val (shown, summary) = AlertNotifications.shown(context)
        val plan = planAlertNotifications(answer.alerts, before, owner, settings::notifies, shown, summary)
        AlertNotifications.apply(context, plan, shown, private())
        store.write(plan.seen)
        return AlertPollResult.Done(plan)
    }

    private companion object {
        const val LOG_TAG = "AlertNotifications"
    }
}

/** The periodic poll (every [AlertNotifications.POLL_MINUTES] minutes, when online). A failed poll waits for the next. */
class AlertPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanceApp
        val poller = AlertPoller(app, app.alertSettings, SeenAlertStore.of(app), private = { app.notificationsPrivate })
        poller.poll(app.backgroundAlertsApi(), app.accountKey, first = inputData.getBoolean(FIRST, false))
        return Result.success()
    }

    companion object {
        /** Input: true for a first poll, which notifies nothing. */
        const val FIRST = "first"
    }
}
