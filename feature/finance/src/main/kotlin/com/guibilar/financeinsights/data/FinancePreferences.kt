package com.guibilar.financeinsights.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The finance module's own settings, kept on the phone: its notifications and its widget. They share the app's
 * settings file and keep their keys, so settings chosen before the module was split off stay as they were.
 */
class FinancePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

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
        const val KEY_BUDGET_ALERTS = "budgetAlerts"
        const val KEY_HIDE_WIDGET_AMOUNTS = "hideWidgetAmounts"
        const val KEY_PRICE_ALERTS = "priceAlerts"
        const val KEY_MONTHLY_REVIEW = "monthlyReview"
        const val KEY_IMPORT_REMINDERS = "importReminders"
    }
}
