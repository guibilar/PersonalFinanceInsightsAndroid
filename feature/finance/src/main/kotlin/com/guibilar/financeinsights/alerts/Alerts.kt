package com.guibilar.financeinsights.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.guibilar.financeinsights.QuickAction
import com.guibilar.financeinsights.finance.R
import com.guibilar.financeinsights.financeHost
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.BudgetAlert
import com.guibilar.financeinsights.core.logic.budgetAlerts
import com.guibilar.financeinsights.core.logic.budgetLevelsDropped
import com.guibilar.financeinsights.core.logic.reviewAlert
import com.guibilar.financeinsights.core.logic.reviewKey
import com.guibilar.financeinsights.core.logic.staleAlertKeys
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.widget.SpendingWidget
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The local finance notifications: a check every few hours (when online) for budgets passing 80% and for the monthly
 * review. All other notifications come from the server alerts, which the app polls (T68, `AlertNotifications` in the
 * app): a budget over its limit, price changes, statements to import, and more. These two rules have no server alert.
 */
object Alerts {
    private const val WORK = "alerts"

    /** The work's name before it ran every alert, queued with a worker class that no longer exists. */
    private const val OLD_WORK = "budget-alerts"
    const val CHANNEL = "budgets"
    private const val NOW_WORK = "alerts-now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun periodic() = PeriodicWorkRequestBuilder<AlertWorker>(6, TimeUnit.HOURS).setConstraints(online).build()

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic())
        // And once now, so turning them on shows a budget already past a threshold. One at a time: switches turned on
        // one after another would queue checks running together, each sending what the others haven't recorded yet.
        work.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(online).build())
    }

    /** On start: keeps the check queued while any alert is on, moving it off the old work name. */
    fun keepScheduled(context: Context, preferences: FinancePreferences) {
        if (!preferences.anyAlerts) return
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(OLD_WORK)
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, periodic())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Checks in the background while a local notification is on (budget warnings, the monthly review). */
    fun reschedule(context: Context, preferences: FinancePreferences) {
        if (preferences.anyAlerts) schedule(context) else cancel(context)
    }

    /** The monthly review, apart from the budget warnings in the phone's notification settings. */
    const val INSIGHTS_CHANNEL = "insights"

    /** The channel of the old import reminders, removed in T68 (the server alert "statements-not-imported" tells it). */
    private const val OLD_REMINDERS_CHANNEL = "reminders"

    /**
     * The "Budget warnings" and "Monthly review" channels, in the phone's notification settings. The ids stay, so a
     * channel that the person silenced stays silenced; only the names change.
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Budget warnings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a budget is nearly used up (80%)"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(INSIGHTS_CHANNEL, "Monthly review", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The month before in review, early each month"
            },
        )
        manager.deleteNotificationChannel(OLD_REMINDERS_CHANNEL)
    }

    /** A notification on the insights channel that opens [action]'s screen; [id] replaces an earlier one of it. */
    internal fun notifyInsight(
        context: Context,
        id: Int,
        action: String,
        title: String,
        text: String,
        private: Boolean,
        publicTitle: String,
        channel: String = INSIGHTS_CHANNEL,
    ) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, context.financeHost.mainActivity).setAction(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            // The same alert posted again (same id) replaces it quietly.
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (private) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, channel)
                            .setSmallIcon(R.drawable.ic_stat_wallet)
                            .setContentTitle(publicTitle)
                            .setContentIntent(open)
                            .build(),
                    )
                }
            }
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Allowed (Android 13's permission) and not turned off for the app in the phone's settings. */
    fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    internal fun notify(context: Context, alert: BudgetAlert, private: Boolean = false) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            alert.budgetId.hashCode(),
            Intent(context, context.financeHost.mainActivity).setAction(QuickAction.Budgets.intentAction).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                // With the app lock on, the phone's lock screen shows that a budget needs a look, not the amounts.
                if (private) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat_wallet)
                            .setContentTitle("A budget needs a look")
                            .setContentIntent(open)
                            .build(),
                    )
                }
            }
            .build()
        // One notification per budget: a later alert replaces the earlier one.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(alert.budgetId.hashCode(), notification)
    }
}

/** Which alerts were sent, so each goes once per budget and period. */
internal class SentAlerts(context: Context) {
    private val prefs = context.getSharedPreferences("budget-alerts", Context.MODE_PRIVATE)

    fun all(): Map<String, Int> = prefs.all.mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }.toMap()

    fun record(alert: BudgetAlert) = record(alert.key, alert.level)

    fun record(key: String, level: Int) {
        prefs.edit { putInt(key, level) }
    }

    fun forget(keys: Collection<String>) {
        if (keys.isNotEmpty()) prefs.edit { keys.forEach(::remove) }
    }

    /**
     * Whose alerts these are ("server|username"): another account signed in starts over, as its budgets and
     * subscriptions are its own (and the last one's mustn't keep its alerts quiet).
     */
    fun keepFor(owner: String) {
        val before = prefs.getString(KEY_OWNER, null)
        if (before == owner) return
        prefs.edit {
            // None yet (recorded before owners were): the same account's, most likely; kept, so nothing is told twice.
            if (before != null) clear()
            putString(KEY_OWNER, owner)
        }
    }

    private companion object {
        /** A string among the levels: [all] reads only the numbers. */
        const val KEY_OWNER = "owner"
    }
}

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = applicationContext.financeHost
        val preferences = host.financePreferences
        // The widget refreshes on the same schedule, alerts or not.
        SpendingWidget.refresh(applicationContext)
        // Without permission to notify, nothing is marked sent: they show once it is given.
        if (!preferences.anyAlerts || !Alerts.canNotify(applicationContext)) return Result.success()
        val api = host.backgroundApi() ?: return Result.success()
        val today = LocalDate.now()
        val sent = SentAlerts(applicationContext)
        sent.keepFor(host.accountKey)
        sent.forget(staleAlertKeys(sent.all().keys, today))
        val private = host.notificationsPrivate
        var retry = false
        // A failed call is tried again later, unless the session ended: that waits for the app to sign in again.
        fun <T> kotlin.Result<T>.orRetry(): T? = getOrNull().also {
            if (it == null && apiError?.isUnauthorized != true) retry = true
        }

        if (preferences.budgetAlerts.value) {
            val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.orRetry()
            if (budgets != null) budgetLevelsDropped(budgets.items, today, sent.all()).forEach { (key, level) ->
                if (level == 0) sent.forget(listOf(key)) else sent.record(key, level)
            }
            if (budgets != null) budgetAlerts(budgets.items, today, sent.all()).forEach { alert ->
                Alerts.notify(applicationContext, alert, private)
                sent.record(alert)
            }
        }
        if (preferences.monthlyReview.value) {
            val key = reviewKey(today)
            if (key != null && sent.all()[key] == null) {
                val review = apiCall { api.review() }.orRetry()
                if (review != null) {
                    val alert = reviewAlert(review)
                    Alerts.notifyInsight(
                        applicationContext, key.hashCode(), QuickAction.Review.intentAction, alert.first, alert.second, private,
                        "Last month in review",
                    )
                    sent.record(key, 1)
                }
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}
