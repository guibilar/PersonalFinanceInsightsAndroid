package com.guibilar.financeinsights.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On a tablet held sideways, the app's tabs are a rail; the finance pages' own layouts are the finance module's tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-mdpi")
class TabletShellTest {
    @get:Rule val compose = createComposeRule()
    private val account = AccountInfo("dev", "https://finance.example.ts.net")

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    private fun bounds(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot()

    @Test
    fun `a transaction opens over a centred list`() {
        show { MainScreen(FakeFinanceApi(), account, {}) }
        compose.onNode(hasText("Transactions") and isSelectable()).performClick()
        compose.waitForIdle()
        assertTrue("a centred column", bounds("Search descriptions and notes").left > 200.dp)
        shoot("tablet-transactions")
        compose.onNode(hasContentDescription("NO FRILLS #3121", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Counts as").assertExists()
        shoot("tablet-transaction-sheet")
    }
}
