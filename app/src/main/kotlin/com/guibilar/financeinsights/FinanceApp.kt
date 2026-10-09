package com.guibilar.financeinsights

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.guibilar.financeinsights.alerts.Alerts
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.AuthState
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.widget.SpendingWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Something asked of the app from outside it: the launcher shortcut, the widget, a notification. */
enum class QuickAction { AddTransaction, Budgets, Subscriptions, Review, Import }

class FinanceApp : Application() {
    /** One per process: the screens reach the API through it. */
    lateinit var session: Session
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var lock: AppLock
        private set

    /** Statements shared to the app, until the import sheet takes them (after signing in, if need be). */
    val sharedStatement = MutableStateFlow<List<PickedFile>?>(null)

    /** A shortcut, widget or notification tap, until the screens act on it. */
    val quickAction = MutableStateFlow<QuickAction?>(null)

    /** Work that lives as long as the app (the widget following the session). */
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        session = Session(SessionStore(this))
        preferences = AppPreferences(this)
        lock = AppLock(preferences)
        // Privacy mode, read as Compose state: every screen that formatted an amount redraws when it's switched.
        val amountsHidden = mutableStateOf(preferences.hideAmounts.value)
        Money.hidden = { amountsHidden.value }
        Alerts.createChannel(this)
        Alerts.keepScheduled(this, preferences)
        scope.launch { preferences.hideAmounts.collect { amountsHidden.value = it } }
        // The widget follows signing in and out, and hiding its amounts.
        scope.launch {
            combine(
                session.state.map { it is AuthState.SignedIn }.distinctUntilChanged(),
                preferences.hideWidgetAmounts,
                preferences.hideAmounts,
            ) { _, _, _ -> }
                .drop(1)
                .collect {
                    if (session.state.value !is AuthState.SignedIn) SpendingWidget.forget(this@FinanceApp)
                    SpendingWidget.refresh(this@FinanceApp)
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
                    .setIntent(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_TRANSACTION))
                    .build(),
            ),
        )
    }
}
