package com.guibilar.financeinsights

import androidx.compose.runtime.DisposableEffect
import androidx.activity.SystemBarStyle
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
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
        // With the lock on, Recents shows no snapshot of the amounts: the lock only covers them on coming back.
        // Android 13 on; before it, the only way (FLAG_SECURE) would also block the person's own screenshots.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch { app.preferences.lock.collect { setRecentsScreenshotEnabled(!it) } }
        }
        setContent {
            val theme by app.preferences.theme.collectAsState()
            val dynamicColor by app.preferences.dynamicColor.collectAsState()
            val dark = when (theme) {
                ThemeChoice.System -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            // The bars' icons follow the app's choice, not the phone's: dark icons on the app's dark background
            // (the phone light, the app set to Dark) can't be read.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            FinanceTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                FinanceRoot(app.session, app.sharedStatement, app.quickAction, app.lock, app.preferences, app.financePreferences)
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
        // Reopened from Recents after Back closed it, the app is handed the intent that first started it again: a
        // statement shared back then (no longer readable), or a shortcut's or notification's page. Not asked for now.
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        when (val action = QuickAction.of(intent.action)) {
            null -> takeStatement(intent)
            else -> {
                // Ranks the "Add transaction" shortcut among the ones the launcher offers.
                if (action == QuickAction.AddTransaction) ShortcutManagerCompat.reportShortcutUsed(this, "add_transaction")
                app.quickAction.value = action
            }
        }
    }

    /** Statements shared to the app (one or several), or one opened with it, wait for the import sheet. */
    private fun takeStatement(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { readFile(this@MainActivity, it) } }
            // Unreadable, they used to vanish without a word.
            if (files.isNotEmpty()) app.sharedStatement.value = files
            val unreadable = uris.size - files.size
            if (unreadable > 0) {
                val text = if (uris.size == 1) "Could not read that file." else "Could not read $unreadable of the ${uris.size} files."
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}

// The scrims androidx.activity uses for 3-button navigation by default.
private val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
