package com.guibilar.financeinsights

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.guibilar.financeinsights.ui.FinanceRoot
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.readFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val app get() = application as FinanceApp

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takeStatement(intent)
        setContent { FinanceTheme { FinanceRoot(app.session, app.sharedStatement) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeStatement(intent)
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
}
