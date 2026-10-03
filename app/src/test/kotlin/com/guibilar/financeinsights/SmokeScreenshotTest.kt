package com.guibilar.financeinsights

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun renders() {
        compose.setContent { Text("Hello") }
        compose.onRoot().captureRoboImage("build/shots/smoke.png")
    }
}
