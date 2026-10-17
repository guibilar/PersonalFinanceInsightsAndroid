package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeTodayApi
import com.guibilar.financeinsights.fakeApiClient
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.notifications.AlertNotificationSettings
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
        compose.setContent { FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) { Surface { content() } } }
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
        val alertSettings = AlertNotificationSettings(ApplicationProvider.getApplicationContext())
        show { AppSettingsScreen(preferences, financePreferences, null, PaddingValues(), onBack = {}, alertSettings = alertSettings) }
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeChoice.Dark, preferences.theme.value)
        shoot("settings")
    }

    @Test
    @Config(qualifiers = "w400dp-h900dp-xxhdpi")
    fun notificationSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val alertSettings = AlertNotificationSettings(context).apply {
            setEnabled(true)
            setDocuments(false)
        }
        show { AppSettingsScreen(AppPreferences(context), FinancePreferences(context), null, PaddingValues(), onBack = {}, alertSettings = alertSettings) }
        compose.onNodeWithText("Monthly review").performScrollTo()
        // A module switch: Money off.
        compose.onNodeWithText("Money").performClick()
        assertEquals(false, alertSettings.money.value)
        // Tasks (T84) has its own switch too.
        compose.onNodeWithText("Tasks").performScrollTo().performClick()
        assertEquals(false, alertSettings.tasks.value)
        compose.onNodeWithText("Tasks").performClick()
        assertEquals(true, alertSettings.tasks.value)
        // And Home (T88).
        compose.onNodeWithText("Warranties that end soon.").performScrollTo().performClick()
        assertEquals(false, alertSettings.home.value)
        compose.onNodeWithText("Warranties that end soon.").performClick()
        assertEquals(true, alertSettings.home.value)
        shoot("settings-notifications")
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
        show { MainScreen(fakeApiClient(FakeFinanceApi(), FakeTodayApi(), FakeDocumentsApi(), FakeAlertsApi(), FakeTasksApi()), account, {}) }
        compose.onNode(hasText("Modules") and isSelectable()).performClick()
        compose.waitForIdle()
        listOf("Today", "Calendar", "Add", "Modules", "Search").forEach { compose.onNode(hasText(it) and isSelectable()).assertExists() }
        shoot("tabs-modules")
    }
}
