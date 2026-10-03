package com.guibilar.financeinsights

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.ui.FinanceRoot
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.readFile
import com.guibilar.financeinsights.widget.SpendingWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A FragmentActivity, as the fingerprint prompt needs one. */
class MainActivity : FragmentActivity() {
    private val app get() = application as FinanceApp

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        setContent {
            val theme by app.preferences.theme.collectAsState()
            val dynamicColor by app.preferences.dynamicColor.collectAsState()
            val dark = when (theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            FinanceTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                FinanceRoot(app.session, app.sharedStatement, app.quickAction, app.lock, app.preferences)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    override fun onStart() {
        super.onStart()
        app.lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        app.lock.onBackground()
        // The widget shows what was just added or changed.
        lifecycleScope.launch { SpendingWidget.refresh(applicationContext) }
    }

    private fun take(intent: Intent?) {
        when (intent?.action) {
            ACTION_ADD_TRANSACTION -> app.quickAction.value = QuickAction.AddTransaction
            ACTION_BUDGETS -> app.quickAction.value = QuickAction.Budgets
            else -> takeStatement(intent)
        }
    }

    /** A statement shared to the app, or opened with it, waits for the import sheet. */
    private fun takeStatement(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { readFile(this@MainActivity, uri) }
            // Unreadable, it used to vanish without a word.
            if (file != null) app.sharedStatement.value = file
            else Toast.makeText(this@MainActivity, "Could not read that file.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_ADD_TRANSACTION = "com.guibilar.financeinsights.ADD_TRANSACTION"
        const val ACTION_BUDGETS = "com.guibilar.financeinsights.BUDGETS"
    }
}
