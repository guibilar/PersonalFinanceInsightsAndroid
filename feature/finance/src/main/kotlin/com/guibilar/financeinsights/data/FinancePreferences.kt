package com.guibilar.financeinsights.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The finance module's own settings, kept on the phone: its local notifications and its widget. They share the app's
 * settings file and keep their keys, so settings chosen before the module was split off stay as they were.
 *
 * Since T68, most finance notifications come from the server alerts (the app's `AlertNotificationSettings`, which also
 * moved the old "priceAlerts" and "importReminders" settings there). The budget warning at 80% is a server alert
 * since T71. Only one local rule stays: the monthly review, which has no server alert yet (T82).
 */
class FinancePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val _hideWidgetAmounts = MutableStateFlow(prefs.getBoolean(KEY_HIDE_WIDGET_AMOUNTS, false))
    /** The home-screen widget shows no amounts (anyone can see the home screen). Its own choice, not the lock's. */
    val hideWidgetAmounts: StateFlow<Boolean> = _hideWidgetAmounts.asStateFlow()

    private val _monthlyReview = MutableStateFlow(prefs.getBoolean(KEY_MONTHLY_REVIEW, false))
    /** A notification early each month with the month before in review. */
    val monthlyReview: StateFlow<Boolean> = _monthlyReview.asStateFlow()

    /** Any local notification is on: the background check runs. */
    val anyAlerts get() = monthlyReview.value

    fun setHideWidgetAmounts(value: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_WIDGET_AMOUNTS, value) }
        _hideWidgetAmounts.value = value
    }

    fun setMonthlyReview(value: Boolean) {
        prefs.edit { putBoolean(KEY_MONTHLY_REVIEW, value) }
        _monthlyReview.value = value
    }

    private companion object {
        const val KEY_HIDE_WIDGET_AMOUNTS = "hideWidgetAmounts"
        const val KEY_MONTHLY_REVIEW = "monthlyReview"
    }
}
