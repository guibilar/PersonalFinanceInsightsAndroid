package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.ui.modules.MODULES
import com.guibilar.financeinsights.ui.modules.ModulesScreen
import com.guibilar.financeinsights.ui.shell.CaptureScreen
import com.guibilar.financeinsights.ui.shell.PlaceholderScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The top-level pages, to look at (build/shots). Today is in TodayScreenTest; the Money home is in the finance module's tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(darkTheme = dark, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    @Test
    fun modules() {
        val opened = mutableListOf<String>()
        show { ModulesScreen(MODULES, account, "1.0", PaddingValues(), open = { opened += it.id }, openSettings = {}, openAccount = {}) }
        shoot("modules")
        // Money, Documents, Tasks, Home and People open; the others are coming soon.
        compose.onNodeWithText("Money").performClick()
        compose.onNodeWithText("Documents").performClick()
        compose.onNodeWithText("Tasks").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("People").performClick()
        assertEquals(listOf("money", "documents", "tasks", "home", "people"), opened)
    }

    @Test
    fun capture() {
        show { CaptureScreen(PaddingValues(), onExpense = {}) }
        shoot("capture")
    }

    @Test
    fun timeline() {
        show { PlaceholderScreen("Timeline", Icons.Outlined.Timeline, "Everything that happened, day by day, from all modules.", PaddingValues()) }
        shoot("timeline")
    }
}
