package com.guibilar.financeinsights.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or as the phone is set. */
enum class ThemeChoice(val label: String) { System("As the phone"), Light("Light"), Dark("Dark") }

/**
 * The app's own settings (not the account's): how it looks, locking it, and privacy mode. Kept on the phone. The
 * finance module keeps its notifications and widget in the same file (`FinancePreferences`).
 */
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

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_LOCK = "lock"
        const val KEY_HIDE_AMOUNTS = "hideAmounts"
    }
}
