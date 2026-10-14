package com.guibilar.financeinsights

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.guibilar.financeinsights.alerts.Alerts
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.today.AlertsApi
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.AuthState
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore
import com.guibilar.financeinsights.notifications.AlertNotificationSettings
import com.guibilar.financeinsights.notifications.AlertNotifications
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.widget.SpendingWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FinanceApp : Application(), FinanceHost {
    /** One per process: the screens reach the API through it. */
    lateinit var session: Session
        private set

    lateinit var preferences: AppPreferences
        private set

    private lateinit var finance: FinancePreferences
    override val financePreferences get() = finance

    lateinit var lock: AppLock
        private set

    /** The alert notifications: on or off, and per module (T68). */
    lateinit var alertSettings: AlertNotificationSettings
        private set

    /** Statements shared to the app, until the import sheet takes them (after signing in, if need be). */
    val sharedStatement = MutableStateFlow<List<PickedFile>?>(null)

    /** A shortcut, widget or notification tap, until the screens act on it. */
    val quickAction = MutableStateFlow<QuickAction?>(null)

    /** The link of a tapped alert notification ("" for Today), until the screens open it (after signing in). */
    val alertLink = MutableStateFlow<String?>(null)

    /** Work that lives as long as the app (the widget following the session). */
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        session = Session(SessionStore(this))
        preferences = AppPreferences(this)
        finance = FinancePreferences(this)
        // Before the old alert settings are read anywhere: it moves them (once).
        alertSettings = AlertNotificationSettings(this)
        lock = AppLock(preferences)
        // Privacy mode, read as Compose state: every screen that formatted an amount redraws when it's switched.
        val amountsHidden = mutableStateOf(preferences.hideAmounts.value)
        Money.hidden = { amountsHidden.value }
        Alerts.createChannel(this)
        Alerts.keepScheduled(this, financePreferences)
        AlertNotifications.createChannel(this)
        AlertNotifications.keepScheduled(this, alertSettings)
        scope.launch { preferences.hideAmounts.collect { amountsHidden.value = it } }
        // The widget follows signing in and out, and hiding its amounts.
        scope.launch {
            combine(
                session.state.map { it is AuthState.SignedIn }.distinctUntilChanged(),
                financePreferences.hideWidgetAmounts,
                preferences.hideAmounts,
            ) { _, _, _ -> }
                .drop(1)
                .collect {
                    if (session.state.value !is AuthState.SignedIn) SpendingWidget.forget(this@FinanceApp)
                    SpendingWidget.refresh(this@FinanceApp)
                }
        }
        // Signed out (by the person, or the session ended): the alert notifications go, and the next poll after signing
        // in is a first poll, which notifies nothing.
        scope.launch {
            session.state.map { it is AuthState.SignedOut }.distinctUntilChanged().drop(1).filter { it }.collect {
                AlertNotifications.forget(this@FinanceApp)
            }
        }
        // Long-pressing the app's icon offers "Add transaction". Set once: the app also starts in the background (for
        // budget checks), where setting shortcuts on every start runs into Android's rate limit.
        if (ShortcutManagerCompat.getDynamicShortcuts(this).none { it.id == "add_transaction" }) ShortcutManagerCompat.setDynamicShortcuts(
            this,
            listOf(
                ShortcutInfoCompat.Builder(this, "add_transaction")
                    .setShortLabel("Add transaction")
                    .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_add))
                    .setIntent(Intent(this, MainActivity::class.java).setAction(QuickAction.AddTransaction.intentAction))
                    .build(),
            ),
        )
    }

    // What the finance module asks of the app, for the widget and the notifications.
    override val mainActivity get() = MainActivity::class.java
    override val amountsHidden get() = preferences.hideAmounts.value
    override val notificationsPrivate get() = preferences.lock.value
    override val accountKey get() = "${session.server}|${session.lastUsername}"
    override fun backgroundApi(): FinanceApi? = session.backgroundClient()?.api<FinanceApi>()

    /** The Alerts API for the background poll (T68); null when signed out. */
    fun backgroundAlertsApi(): AlertsApi? = session.backgroundClient()?.api<AlertsApi>()
}
