package com.guibilar.financeinsights

import android.app.Activity
import android.app.Application
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.data.FinancePreferences

/** The app around the finance module in its tests (robolectric.properties): signed out, nothing hidden. */
class TestFinanceApp : Application(), FinanceHost {
    override val mainActivity: Class<out Activity> = Activity::class.java
    override val financePreferences by lazy { FinancePreferences(this) }
    override val amountsHidden = false
    override val notificationsPrivate = false
    override val accountKey = "https://finance.example.ts.net|dev"
    override fun backgroundApi(): FinanceApi? = null
}
