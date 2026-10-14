package com.guibilar.financeinsights.notifications

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The settings of the alert notifications (T68): on or off, and on or off per module. Kept on the phone, in the app's
 * settings file (with `AppPreferences` and `FinancePreferences`).
 *
 * Migration of the old local notifications (once, on the first start after T68):
 * - The notifications are on when the old "Budget alerts", "Subscription price changes" or "Import reminders" switch
 *   was on: the server alerts tell these now ("budget-over", "price-changed", "statements-not-imported").
 * - Money and Documents start on. Tasks (T84) starts on too: a new key, so no migration step.
 * - The old "priceAlerts" and "importReminders" settings are removed. "budgetAlerts" stays: it is now the local
 *   warning at 80% (no server alert yet), with "monthlyReview" (`FinancePreferences`).
 */
class AlertNotificationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    /** The phone polls the server alerts and notifies new ones. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _money = MutableStateFlow(prefs.getBoolean(KEY_MONEY, true))
    /** Notifications for the alerts of the Money module. */
    val money: StateFlow<Boolean> = _money.asStateFlow()

    private val _documents = MutableStateFlow(prefs.getBoolean(KEY_DOCUMENTS, true))
    /** Notifications for the alerts of the Documents module. */
    val documents: StateFlow<Boolean> = _documents.asStateFlow()

    private val _tasks = MutableStateFlow(prefs.getBoolean(KEY_TASKS, true))
    /** Notifications for the alerts of the Tasks module (overdue tasks). */
    val tasks: StateFlow<Boolean> = _tasks.asStateFlow()

    /** Whether the alerts of [module] give notifications. A module with no switch here (a newer one) does. */
    fun notifies(module: String): Boolean = when (module) {
        MONEY -> money.value
        DOCUMENTS -> documents.value
        TASKS -> tasks.value
        else -> true
    }

    fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    fun setMoney(value: Boolean) {
        prefs.edit { putBoolean(KEY_MONEY, value) }
        _money.value = value
    }

    fun setDocuments(value: Boolean) {
        prefs.edit { putBoolean(KEY_DOCUMENTS, value) }
        _documents.value = value
    }

    fun setTasks(value: Boolean) {
        prefs.edit { putBoolean(KEY_TASKS, value) }
        _tasks.value = value
    }

    private fun migrate() {
        if (prefs.contains(KEY_VERSION)) return
        val wasOn = OLD_KEYS.any { prefs.getBoolean(it, false) }
        prefs.edit {
            putBoolean(KEY_ENABLED, wasOn)
            putBoolean(KEY_MONEY, true)
            putBoolean(KEY_DOCUMENTS, true)
            remove(OLD_PRICE_ALERTS)
            remove(OLD_IMPORT_REMINDERS)
            putInt(KEY_VERSION, VERSION)
        }
    }

    companion object {
        /** The module names of the server (`AlertDto.module`). */
        const val MONEY = "Money"
        const val DOCUMENTS = "Documents"
        const val TASKS = "Tasks"

        private const val KEY_ENABLED = "alertNotifications"
        private const val KEY_MONEY = "alertNotifications.Money"
        private const val KEY_DOCUMENTS = "alertNotifications.Documents"
        private const val KEY_TASKS = "alertNotifications.Tasks"
        private const val KEY_VERSION = "alertNotifications.version"
        private const val VERSION = 1

        // The switches of the old local rules (before T68).
        private const val OLD_BUDGET_ALERTS = "budgetAlerts"
        private const val OLD_PRICE_ALERTS = "priceAlerts"
        private const val OLD_IMPORT_REMINDERS = "importReminders"
        private val OLD_KEYS = listOf(OLD_BUDGET_ALERTS, OLD_PRICE_ALERTS, OLD_IMPORT_REMINDERS)
    }
}
