package com.guibilar.financeinsights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.guibilar.financeinsights.ui.FinanceRoot
import com.guibilar.financeinsights.ui.theme.FinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val session = (application as FinanceApp).session
        setContent { FinanceTheme { FinanceRoot(session) } }
    }
}
