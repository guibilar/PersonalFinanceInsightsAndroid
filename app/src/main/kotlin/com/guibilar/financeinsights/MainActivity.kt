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
import com.guibilar.financeinsights.notifications.AlertNotifications
import com.guibilar.financeinsights.ui.FinanceRoot
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.documents.ui.preparePhoto
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
                FinanceRoot(
                    app.session, app.sharedStatement, app.quickAction, app.lock, app.preferences, app.financePreferences,
                    alertLink = app.alertLink, alertSettings = app.alertSettings,
                )
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
        // T81: a new alert shows soon after the app opens, not only at the hourly poll.
        AlertNotifications.pollSoon(this, app.alertSettings)
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
        // An alert notification (T68): its page opens once signed in, through the same link map as Today.
        if (intent.action == AlertNotifications.ACTION_OPEN_ALERT) {
            app.alertLink.value = intent.getStringExtra(AlertNotifications.EXTRA_LINK).orEmpty()
            return
        }
        when (val action = QuickAction.of(intent.action)) {
            null -> takeStatement(intent)
            else -> {
                // Ranks the "Add transaction" shortcut among the ones the launcher offers.
                if (action == QuickAction.AddTransaction) ShortcutManagerCompat.reportShortcutUsed(this, "add_transaction")
                app.quickAction.value = action
            }
        }
    }

    /**
     * What another app shares to Hestia, or a file opened with it (T101): statements (CSV) wait for the import sheet as
     * before; text and links, photos, PDFs, .ics, and .vcf files wait in [Shares] for their screens.
     */
    private fun takeStatement(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isEmpty()) {
            // Text or a link shared from another app: a note for the Inbox.
            val text = intent.takeIf { it.action == Intent.ACTION_SEND }?.let {
                listOfNotNull(it.getStringExtra(Intent.EXTRA_SUBJECT), it.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
                    .map(String::trim).filter(String::isNotEmpty).distinct().joinToString("\n")
            }
            if (!text.isNullOrBlank()) Shares.pending.value = Share.Text(text)
            return
        }
        lifecycleScope.launch {
            val read = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val file = readShared(this@MainActivity, uri) ?: return@mapNotNull null
                    Triple(sharedKind(contentResolver.getType(uri), file.name), file, uri)
                }
            }
            val unreadable = uris.size - read.size
            if (unreadable > 0) {
                val text = if (uris.size == 1) "Could not read that file." else "Could not read $unreadable of the ${uris.size} files."
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
            if (read.isEmpty()) return@launch
            val kinds = read.map { it.first }.toSet()
            val files = read.map { it.second }
            when {
                kinds == setOf(SharedKind.Statement) -> app.sharedStatement.value = files
                kinds == setOf(SharedKind.Calendar) -> Shares.pending.value = Share.Calendar(files.first().bytes.decodeToString())
                kinds == setOf(SharedKind.Contacts) -> Shares.pending.value = Share.Contacts(files.joinToString("\n") { it.bytes.decodeToString() })
                kinds == setOf(SharedKind.Pdf) ->
                    Shares.pending.value = Share.Pdfs(files.map { UploadFile(it.name, it.bytes) }, files)
                else -> {
                    // Photos (with PDFs or others): a document of these files. Photos get the upload's size limit.
                    val uploads = withContext(Dispatchers.Default) {
                        read.map { (kind, file, _) ->
                            val upload = UploadFile(file.name, file.bytes)
                            if (kind == SharedKind.Image) preparePhoto(upload) else upload
                        }
                    }
                    Shares.pending.value = Share.Files(uploads)
                }
            }
        }
    }
}

// The scrims androidx.activity uses for 3-button navigation by default.
private val LIGHT_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
