package com.guibilar.financeinsights.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.ui.signin.UnreachableScreen
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screens from the layout audit: the unreachable server (the dashboard at the largest text is the finance module's). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class UnreachableScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unreachableAtLaunch() {
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                UnreachableScreen("https://finance.example.ts.net", "Could not reach the server. Is Tailscale on?", {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/unreachable.png")
    }
}
