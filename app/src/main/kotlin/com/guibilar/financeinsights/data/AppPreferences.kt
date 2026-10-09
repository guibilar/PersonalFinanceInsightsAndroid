package com.guibilar.financeinsights.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or as the phone is set. */
enum class ThemeChoice(val label: String) { System("As the phone"), Light("Light"), Dark("Dark") }

/** The app's own settings (not the account's): how it looks, locking it, and notifications. Kept on the phone. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        ThemeChoice.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeChoice.System,
    )
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    /** Colours from the wallpaper (Android 12 and later), or the app's own blue. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _lock = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    /** Opening the app asks for a fingerprint, face or the phone's PIN. */
    val lock: StateFlow<Boolean> = _lock.asStateFlow()

    private val _hideAmounts = MutableStateFlow(prefs.getBoolean(KEY_HIDE_AMOUNTS, false))
    /** Privacy mode: every amount in the app, the widget and the notifications reads "$•••••". */
    val hideAmounts: StateFlow<Boolean> = _hideAmounts.asStateFlow()

    private val _hideWidgetAmounts = MutableStateFlow(prefs.getBoolean(KEY_HIDE_WIDGET_AMOUNTS, false))
    /** The home-screen widget shows no amounts (anyone can see the home screen). Its own choice, not the lock's. */
    val hideWidgetAmounts: StateFlow<Boolean> = _hideWidgetAmounts.asStateFlow()

    private val _budgetAlerts = MutableStateFlow(prefs.getBoolean(KEY_BUDGET_ALERTS, false))
    /** A notification when a budget passes 80% and 100%. */
    val budgetAlerts: StateFlow<Boolean> = _budgetAlerts.asStateFlow()

    private val _priceAlerts = MutableStateFlow(prefs.getBoolean(KEY_PRICE_ALERTS, false))
    /** A notification when a subscription's fixed price changes. */
    val priceAlerts: StateFlow<Boolean> = _priceAlerts.asStateFlow()

    private val _monthlyReview = MutableStateFlow(prefs.getBoolean(KEY_MONTHLY_REVIEW, false))
    /** A notification early each month with the month before in review. */
    val monthlyReview: StateFlow<Boolean> = _monthlyReview.asStateFlow()

    private val _importReminders = MutableStateFlow(prefs.getBoolean(KEY_IMPORT_REMINDERS, false))
    /** A notification after more than a week without importing a statement, and weekly while it stays so. */
    val importReminders: StateFlow<Boolean> = _importReminders.asStateFlow()

    /** Any notification is on: the background check runs. */
    val anyAlerts get() = budgetAlerts.value || priceAlerts.value || monthlyReview.value || importReminders.value

    fun setTheme(value: ThemeChoice) {
        prefs.edit { putString(KEY_THEME, value.name) }
        _theme.value = value
    }

    fun setDynamicColor(value: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }
        _dynamicColor.value = value
    }

    fun setLock(value: Boolean) {
        prefs.edit { putBoolean(KEY_LOCK, value) }
        _lock.value = value
    }

    fun setHideAmounts(value: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_AMOUNTS, value) }
        _hideAmounts.value = value
    }

    fun setHideWidgetAmounts(value: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_WIDGET_AMOUNTS, value) }
        _hideWidgetAmounts.value = value
    }

    fun setBudgetAlerts(value: Boolean) {
        prefs.edit { putBoolean(KEY_BUDGET_ALERTS, value) }
        _budgetAlerts.value = value
    }

    fun setPriceAlerts(value: Boolean) {
        prefs.edit { putBoolean(KEY_PRICE_ALERTS, value) }
        _priceAlerts.value = value
    }

    fun setMonthlyReview(value: Boolean) {
        prefs.edit { putBoolean(KEY_MONTHLY_REVIEW, value) }
        _monthlyReview.value = value
    }

    fun setImportReminders(value: Boolean) {
        prefs.edit { putBoolean(KEY_IMPORT_REMINDERS, value) }
        _importReminders.value = value
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_LOCK = "lock"
        const val KEY_BUDGET_ALERTS = "budgetAlerts"
        const val KEY_HIDE_AMOUNTS = "hideAmounts"
        const val KEY_HIDE_WIDGET_AMOUNTS = "hideWidgetAmounts"
        const val KEY_PRICE_ALERTS = "priceAlerts"
        const val KEY_MONTHLY_REVIEW = "monthlyReview"
        const val KEY_IMPORT_REMINDERS = "importReminders"
    }
}
