package com.guibilar.financeinsights

import android.app.Activity
import android.content.Context
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.data.FinancePreferences

/**
 * What the finance module needs from the app around it, for work outside the screens (the widget, the notifications).
 * The app's Application implements it, so the module never depends on the app.
 */
interface FinanceHost {
    /** The activity that the widget and the notifications open, with a [QuickAction]'s intent action. */
    val mainActivity: Class<out Activity>

    val financePreferences: FinancePreferences

    /** Privacy mode: every amount reads "$•••••". */
    val amountsHidden: Boolean

    /** The app lock is on: the phone's lock screen shows no amounts in notifications. */
    val notificationsPrivate: Boolean

    /** Whose figures these are ("server|username"): alerts sent for one account aren't kept quiet for another. */
    val accountKey: String

    /** The API for work in the background; null when signed out. */
    fun backgroundApi(): FinanceApi?
}

/** The app around the finance module. */
val Context.financeHost: FinanceHost get() = applicationContext as FinanceHost
