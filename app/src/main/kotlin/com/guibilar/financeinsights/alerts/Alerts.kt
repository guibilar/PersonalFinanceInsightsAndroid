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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.guibilar.financeinsights.FinanceApp
import com.guibilar.financeinsights.MainActivity
import com.guibilar.financeinsights.R
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.BudgetAlert
import com.guibilar.financeinsights.core.logic.budgetAlerts
import com.guibilar.financeinsights.core.logic.importReminder
import com.guibilar.financeinsights.core.logic.priceAlerts
import com.guibilar.financeinsights.core.logic.reviewAlert
import com.guibilar.financeinsights.core.logic.reviewKey
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.widget.SpendingWidget
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The notifications: a check every few hours (when online) for budgets passing 80% and 100%, price changes, the monthly
 * review and import reminders.
 */
object Alerts {
    private const val WORK = "alerts"

    /** The work's name before it ran every alert, queued with a worker class that no longer exists. */
    private const val OLD_WORK = "budget-alerts"
    const val CHANNEL = "budgets"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun periodic() = PeriodicWorkRequestBuilder<AlertWorker>(6, TimeUnit.HOURS).setConstraints(online).build()

    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic())
        // And once now, so turning them on shows a budget already past a threshold.
        work.enqueue(OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(online).build())
    }

    /** On start: keeps the check queued while any alert is on, moving it off the old work name. */
    fun keepScheduled(context: Context, preferences: AppPreferences) {
        if (!preferences.anyAlerts) return
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(OLD_WORK)
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, periodic())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Checks in the background while any notification is on (budgets, price changes, the monthly review, import reminders). */
    fun reschedule(context: Context, preferences: AppPreferences) {
        if (preferences.anyAlerts) schedule(context) else cancel(context)
    }

    /** Price changes and the monthly review, apart from the budgets in the phone's notification settings. */
    const val INSIGHTS_CHANNEL = "insights"

    /** The import reminders, a channel of their own so they can be silenced alone. */
    const val REMINDERS_CHANNEL = "reminders"

    /** The "Budgets" and "Subscriptions and reviews" channels, in the phone's notification settings. */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Budgets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a budget is nearly or fully used up"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(INSIGHTS_CHANNEL, "Subscriptions and reviews", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a subscription's price changes, and the month before in review"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS_CHANNEL, "Import reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When no statement has been imported for more than a week"
            },
        )
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
            Intent(context, MainActivity::class.java).setAction(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
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
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_BUDGETS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
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
}

private const val IMPORT_REMINDER_ID = 7_000

class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanceApp
        val preferences = app.preferences
        // The widget refreshes on the same schedule, alerts or not.
        SpendingWidget.refresh(applicationContext)
        // Without permission to notify, nothing is marked sent: they show once it is given.
        if (!preferences.anyAlerts || !Alerts.canNotify(applicationContext)) return Result.success()
        val api = app.session.backgroundApi() ?: return Result.success()
        val sent = SentAlerts(applicationContext)
        val private = preferences.lock.value
        val today = LocalDate.now()
        var retry = false
        // A failed call is tried again later, unless the session ended: that waits for the app to sign in again.
        fun <T> kotlin.Result<T>.orRetry(): T? = getOrNull().also {
            if (it == null && apiError?.isUnauthorized != true) retry = true
        }

        if (preferences.budgetAlerts.value) {
            val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.orRetry()
            if (budgets != null) budgetAlerts(budgets.items, today, sent.all()).forEach { alert ->
                Alerts.notify(applicationContext, alert, private)
                sent.record(alert)
            }
        }
        if (preferences.priceAlerts.value) {
            val recurring = apiCall { api.recurring() }.orRetry()
            if (recurring != null) priceAlerts(recurring, today, sent.all()).forEach { alert ->
                Alerts.notifyInsight(
                    applicationContext, alert.key.hashCode(), MainActivity.ACTION_SUBSCRIPTIONS, alert.title, alert.text, private,
                    "A subscription's price changed",
                )
                sent.record(alert.key, 1)
            }
        }
        if (preferences.monthlyReview.value) {
            val key = reviewKey(today)
            if (key != null && sent.all()[key] == null) {
                val review = apiCall { api.review() }.orRetry()
                if (review != null) {
                    val alert = reviewAlert(review)
                    Alerts.notifyInsight(
                        applicationContext, key.hashCode(), MainActivity.ACTION_REVIEW, alert.first, alert.second, private,
                        "Last month in review",
                    )
                    sent.record(key, 1)
                }
            }
        }
        if (preferences.importReminders.value) {
            val accounts = apiCall { api.accounts() }.orRetry()
            if (accounts != null) importReminder(accounts, today, ZoneId.systemDefault(), sent.all())?.let { reminder ->
                // One at a time: a later week's reminder replaces the one before.
                Alerts.notifyInsight(
                    applicationContext, IMPORT_REMINDER_ID, MainActivity.ACTION_IMPORT, reminder.title, reminder.text, private,
                    "Time to import your statements", Alerts.REMINDERS_CHANNEL,
                )
                sent.record(reminder.key, 1)
            }
        }
        return if (retry) Result.retry() else Result.success()
    }
}
