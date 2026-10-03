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
import com.guibilar.financeinsights.core.logic.BudgetAlert
import com.guibilar.financeinsights.core.logic.budgetAlerts
import com.guibilar.financeinsights.core.model.BudgetFilter
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.widget.SpendingWidget
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Budget alerts: a check every few hours (when online) that notifies once a budget passes 80% and 100%. */
object BudgetAlerts {
    private const val WORK = "budget-alerts"
    const val CHANNEL = "budgets"

    fun schedule(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BudgetAlertWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
        // And once now, so turning them on shows a budget already past a threshold.
        work.enqueue(OneTimeWorkRequestBuilder<BudgetAlertWorker>().setConstraints(online).build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** The "Budgets" channel, in the phone's notification settings. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "Budgets", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a budget is nearly or fully used up"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun notify(context: Context, alert: BudgetAlert) {
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

    fun record(alert: BudgetAlert) {
        prefs.edit().putInt(alert.key, alert.level).apply()
    }
}

class BudgetAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FinanceApp
        // The widget refreshes on the same schedule, alerts or not.
        SpendingWidget.refresh(applicationContext)
        // Without permission to notify, nothing is marked sent: they show once it is given.
        if (!app.preferences.budgetAlerts.value || !BudgetAlerts.canNotify(applicationContext)) return Result.success()
        val api = app.session.backgroundApi() ?: return Result.success()
        val budgets = apiCall { api.budgets(BudgetFilter(status = Status.Active, page = 1, pageSize = 1000)) }.getOrNull()
            ?: return Result.retry()
        val sent = SentAlerts(applicationContext)
        budgetAlerts(budgets.items, LocalDate.now(), sent.all()).forEach { alert ->
            BudgetAlerts.notify(applicationContext, alert)
            sent.record(alert)
        }
        return Result.success()
    }
}
