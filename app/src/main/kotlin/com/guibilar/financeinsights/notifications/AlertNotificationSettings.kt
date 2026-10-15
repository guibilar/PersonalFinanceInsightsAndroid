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
 * - Money and Documents start on. Tasks (T84), Home (T88) and People (T92) start on too: new keys, so no migration step.
 * - The old "priceAlerts" and "importReminders" settings are removed.
 *
 * Version 2 (T71): the budget warning at 80% is a server alert ("budget-warning"). When the old local "Budget warnings"
 * switch ("budgetAlerts") was on, the notifications and Money turn on, and the old setting is removed.
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

    private val _home = MutableStateFlow(prefs.getBoolean(KEY_HOME, true))
    /** Notifications for the alerts of the Home module (warranties that end soon). */
    val home: StateFlow<Boolean> = _home.asStateFlow()

    private val _people = MutableStateFlow(prefs.getBoolean(KEY_PEOPLE, true))
    /** Notifications for the alerts of the People module (reach out, birthdays, lent things not back). */
    val people: StateFlow<Boolean> = _people.asStateFlow()

    /** Whether the alerts of [module] give notifications. A module with no switch here (a newer one) does. */
    fun notifies(module: String): Boolean = when (module) {
        MONEY -> money.value
        DOCUMENTS -> documents.value
        TASKS -> tasks.value
        HOME -> home.value
        PEOPLE -> people.value
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

    fun setHome(value: Boolean) {
        prefs.edit { putBoolean(KEY_HOME, value) }
        _home.value = value
    }

    fun setPeople(value: Boolean) {
        prefs.edit { putBoolean(KEY_PEOPLE, value) }
        _people.value = value
    }

    private fun migrate() {
        val version = prefs.getInt(KEY_VERSION, 0)
        if (version >= VERSION) return
        prefs.edit {
            if (version < 1) {
                putBoolean(KEY_ENABLED, OLD_KEYS.any { prefs.getBoolean(it, false) })
                putBoolean(KEY_MONEY, true)
                putBoolean(KEY_DOCUMENTS, true)
                remove(OLD_PRICE_ALERTS)
                remove(OLD_IMPORT_REMINDERS)
            }
            if (prefs.getBoolean(OLD_BUDGET_ALERTS, false)) {
                putBoolean(KEY_ENABLED, true)
                putBoolean(KEY_MONEY, true)
            }
            remove(OLD_BUDGET_ALERTS)
            putInt(KEY_VERSION, VERSION)
        }
    }

    companion object {
        /** The module names of the server (`AlertDto.module`). */
        const val MONEY = "Money"
        const val DOCUMENTS = "Documents"
        const val TASKS = "Tasks"
        const val HOME = "Home"
        const val PEOPLE = "People"

        private const val KEY_ENABLED = "alertNotifications"
        private const val KEY_MONEY = "alertNotifications.Money"
        private const val KEY_DOCUMENTS = "alertNotifications.Documents"
        private const val KEY_TASKS = "alertNotifications.Tasks"
        private const val KEY_HOME = "alertNotifications.Home"
        private const val KEY_PEOPLE = "alertNotifications.People"
        private const val KEY_VERSION = "alertNotifications.version"
        private const val VERSION = 2

        // The switches of the old local rules (before T68; "budgetAlerts" before T71).
        private const val OLD_BUDGET_ALERTS = "budgetAlerts"
        private const val OLD_PRICE_ALERTS = "priceAlerts"
        private const val OLD_IMPORT_REMINDERS = "importReminders"
        private val OLD_KEYS = listOf(OLD_BUDGET_ALERTS, OLD_PRICE_ALERTS, OLD_IMPORT_REMINDERS)
    }
}
