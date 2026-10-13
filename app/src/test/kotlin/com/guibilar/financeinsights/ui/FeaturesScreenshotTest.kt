package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.ui.settings.AppSettingsScreen
import com.guibilar.financeinsights.ui.signin.LockScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The settings, the lock, and the bar on a narrow phone. (The tablet layouts are in TabletLayoutTest.) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeaturesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xxhdpi")
    fun settings() {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
        val financePreferences = FinancePreferences(ApplicationProvider.getApplicationContext())
        show { AppSettingsScreen(preferences, financePreferences, null, PaddingValues(), onBack = {}) }
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeChoice.Dark, preferences.theme.value)
        shoot("settings")
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xxhdpi")
    fun lock() {
        show(dark = true) { LockScreen(onUnlock = {}) }
        shoot("lock-dark")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun fiveTabsFitAPhone() {
        show { MainScreen(FakeFinanceApi(), account, {}) }
        compose.onNode(hasText("Modules") and isSelectable()).performClick()
        compose.waitForIdle()
        listOf("Today", "Timeline", "Capture", "Modules", "Search").forEach { compose.onNode(hasText(it) and isSelectable()).assertExists() }
        shoot("tabs-modules")
    }
}
