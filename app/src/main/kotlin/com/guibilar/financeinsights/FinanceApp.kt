package com.guibilar.financeinsights

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.guibilar.financeinsights.alerts.BudgetAlerts
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.ui.transactions.PickedFile
import kotlinx.coroutines.flow.MutableStateFlow

/** Something asked of the app from outside it: the launcher shortcut, the widget, a notification. */
enum class QuickAction { AddTransaction, Budgets }

class FinanceApp : Application() {
    /** One per process: the screens reach the API through it. */
    lateinit var session: Session
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var lock: AppLock
        private set

    /** A statement shared to the app, until the import sheet takes it (after signing in, if need be). */
    val sharedStatement = MutableStateFlow<PickedFile?>(null)

    /** A shortcut, widget or notification tap, until the screens act on it. */
    val quickAction = MutableStateFlow<QuickAction?>(null)

    override fun onCreate() {
        super.onCreate()
        session = Session(SessionStore(this))
        preferences = AppPreferences(this)
        lock = AppLock(preferences)
        BudgetAlerts.createChannel(this)
        // Long-pressing the app's icon offers "Add transaction".
        ShortcutManagerCompat.setDynamicShortcuts(
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
