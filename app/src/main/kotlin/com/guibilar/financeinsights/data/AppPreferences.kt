package com.guibilar.financeinsights.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or as the phone is set. */
enum class ThemeChoice(val label: String) { System("As the phone"), Light("Light"), Dark("Dark") }

/** The app's own settings (not the account's): how it looks, locking it, and budget alerts. Kept on the phone. */
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

    private val _budgetAlerts = MutableStateFlow(prefs.getBoolean(KEY_BUDGET_ALERTS, false))
    /** A notification when a budget passes 80% and 100%. */
    val budgetAlerts: StateFlow<Boolean> = _budgetAlerts.asStateFlow()

    fun setTheme(value: ThemeChoice) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        _theme.value = value
    }

    fun setDynamicColor(value: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        _dynamicColor.value = value
    }

    fun setLock(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK, value).apply()
        _lock.value = value
    }

    fun setBudgetAlerts(value: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ALERTS, value).apply()
        _budgetAlerts.value = value
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_LOCK = "lock"
        const val KEY_BUDGET_ALERTS = "budgetAlerts"
    }
}
