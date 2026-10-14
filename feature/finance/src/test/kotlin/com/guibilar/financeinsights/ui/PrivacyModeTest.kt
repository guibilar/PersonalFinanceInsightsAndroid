package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.ui.dashboard.DashboardScreen
import com.guibilar.financeinsights.ui.dashboard.DashboardViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Privacy mode: the dashboard's eye button hides every amount on the screen already showing, and brings them back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class PrivacyModeTest {
    private val alerts = FakeAlertsApi()
    @get:Rule val compose = createComposeRule()
    private val hidden = mutableStateOf(false)

    @After
    fun showAgain() {
        Money.hidden = { false }
    }

    /** Texts on screen with a figure in dollars ("$2,065.19"), and with a hidden one. */
    private fun amounts() = texts().filter { Regex("""\$\d""").containsMatchIn(it) }
    private fun hiddenAmounts() = texts().filter { it.contains(Money.HIDDEN) }
    private fun texts(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } }

    @Test
    fun hidesAndShowsEveryAmount() {
        Money.hidden = { hidden.value }
        val model = DashboardViewModel(FakeFinanceApi(), alerts) { LocalDate.of(2026, 10, 4) }
        compose.setContent {
            FinanceTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    DashboardScreen(
                        model, AccountInfo("dev", "s"), {}, PaddingValues(), {},
                        amountsHidden = hidden.value,
                        toggleAmounts = { hidden.value = !hidden.value },
                    )
                }
            }
        }
        compose.waitForIdle()
        val shown = amounts()
        assertTrue("the dashboard shows amounts", shown.isNotEmpty())

        compose.onNodeWithContentDescription("Hide amounts").performClick()
        compose.waitForIdle()

        assertEquals(emptyList<String>(), amounts())
        assertTrue(hiddenAmounts().isNotEmpty())
        compose.onNode(hasText("Where the month is heading")).assertExists()
        captureScreenRoboImage("build/shots/privacy-dashboard.png")

        compose.onNodeWithContentDescription("Show amounts").performClick()
        compose.waitForIdle()
        assertEquals(shown, amounts())
    }
}
