package com.guibilar.financeinsights.security

import android.os.SystemClock
import com.guibilar.financeinsights.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app is locked: on launch, and on coming back after [GRACE_MS] away, when the lock is on. A short
 * trip out (the file picker, the phone's PIN screen, a rotation) doesn't lock it.
 */
class AppLock(private val preferences: AppPreferences, private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val _locked = MutableStateFlow(preferences.lock.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt: Long? = null

    fun onBackground() {
        leftAt = now()
    }

    fun onForeground() {
        val away = leftAt?.let { now() - it } ?: return
        leftAt = null
        if (preferences.lock.value && away >= GRACE_MS) _locked.value = true
    }

    fun unlocked() {
        _locked.value = false
    }

    /** Turned off in settings: nothing stays locked. Turned on: it locks next time, not now. */
    fun enabledChanged(enabled: Boolean) {
        if (!enabled) _locked.value = false
    }

    companion object {
        const val GRACE_MS = 60_000L
    }
}
