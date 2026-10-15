package com.guibilar.financeinsights.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.guibilar.financeinsights.FakeTodayApi
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.today.MoneyLink
import com.guibilar.financeinsights.core.today.TodayCardType
import com.guibilar.financeinsights.core.today.TodayDestination
import com.guibilar.financeinsights.core.today.TodayDto
import com.guibilar.financeinsights.ui.modules.HestiaModule
import com.guibilar.financeinsights.ui.modules.MODULES
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import com.guibilar.financeinsights.core.today.TodayModule
import com.guibilar.financeinsights.core.today.TodayModuleStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Today from the Today API, with each module status, to look at (build/shots) and to check what opens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1300dp-xxhdpi")
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    private val api = FakeTodayApi()
    private val opened = mutableListOf<TodayDestination>()
    private val openedModules = mutableListOf<HestiaModule>()

    @After
    fun showAmounts() {
        Money.hidden = { false }
    }

    private fun show(answer: TodayDto = TodaySamples.complete, dark: Boolean = false) {
        api.answer = answer
        val model = TodayViewModel(api) { LocalDate.of(2026, 10, 4) }
        compose.setContent {
            FinanceTheme(ripples = false, darkTheme = dark, dynamicColor = false) {
                Surface { TodayScreen(model, MODULES, PaddingValues(), open = { opened += it }, openModule = { openedModules += it }) }
            }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/shots/$name.png")

    private fun assertShows(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }

    @Test
    fun today() = complete(dark = false)

    @Test
    fun todayDark() = complete(dark = true)

    private fun complete(dark: Boolean) {
        show(dark = dark)
        // The server's day, not the phone's.
        assertShows("Saturday, October 3", "Money", "Open Money", "Needs attention")
        // Every module is active since T21: no "Coming soon" section.
        compose.onAllNodesWithText("Coming soon").assertCountEquals(0)
        // The same figures as the Money home on the recorded data.
        assertShows(
            "$186.89", "$42.69 over a usual month by now",
            "-$186.89", "No income yet this month",
            "$7,750.00", "+$1,950.00 since last month",
            "0 of 2 over", "All within limits",
            "NETFLIX went up from $16.49 to $18.99",
            "4 transactions are in Other because no rule names their merchants",
        )
        shoot(if (dark) "today-dark" else "today")
    }

    @Test
    fun `a partial module shows a note and the cards that exist`() {
        show(TodaySamples.partial)
        assertShows("Some Money figures could not load.", "Could not load this", "—", "$186.89", "Needs attention")
        shoot("today-partial")
    }

    @Test
    fun `a failed module offers a retry for that module`() {
        show(TodaySamples.failed)
        assertShows("Money could not load.")
        compose.onAllNodesWithText("Spent this month").assertCountEquals(0)
        shoot("today-failed")

        api.answer = TodaySamples.complete
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()

        assertEquals(listOf(false, true), api.calls)
        assertShows("Spent this month")
    }

    @Test
    fun `a full failure offers a retry for the page`() {
        api.failing = true
        show()
        assertShows("Can't reach the server. Check your connection and that Tailscale is on.")
        shoot("today-offline")

        api.failing = false
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()

        assertShows("Spent this month", "Needs attention")
    }

    @Test
    fun `System and People alerts show in Needs attention, with no empty section, System without a link (T81)`() {
        show(TodaySamples.withSystem)
        assertShows("Money", "Needs attention", "No event broker since Oct 2, 2026", "Reach out to Marco Silva: last contact on Aug 15, 2026")
        compose.onAllNodesWithText("System").assertCountEquals(0)
        compose.onAllNodesWithText("Open People").assertCountEquals(0)
        shoot("today-system")
        compose.onNodeWithText("No event broker since Oct 2, 2026").performClick()
        assertEquals(emptyList<TodayDestination>(), opened)
        compose.onNodeWithText("Reach out to Marco Silva: last contact on Aug 15, 2026").performClick()
        assertEquals(listOf<TodayDestination>(TodayDestination.Person("p1")), opened)
    }

    @Test
    fun `the shown sections leave out modules without tiles, and put System last`() {
        val sections = TodaySamples.withSystem.sections(MODULES)
        assertEquals(listOf("System", "Money", "People"), sections.map { it.name })
        assertEquals(listOf("Money"), sections.shown().map { it.name })
        val failed = TodaySamples.withSystem.copy(
            modules = listOf(TodayModule("System", TodayModuleStatus.Failed), TodayModule("Money", TodayModuleStatus.Complete)),
        )
        assertEquals(listOf("Money", "System"), failed.sections(MODULES).shown().map { it.name })
    }

    @Test
    fun `a module with cards gets its own section`() {
        show(TodaySamples.twoModules)
        assertShows("Documents", "Expiring soon", "Next on Oct 20, 2026", "Groceries is over its limit (112% used)")
        compose.onAllNodesWithText("Documents that expire soon").assertCountEquals(0)
        shoot("today-modules")
        // Documents is active (T12): its section opens its list, and its tile its page.
        compose.onNodeWithText("Open Documents").performClick()
        compose.onNodeWithText("Expiring soon").performClick()
        assertEquals(listOf("documents"), openedModules.map { it.id })
        assertEquals(listOf<TodayDestination>(TodayDestination.Documents), opened)
    }

    @Test
    fun `the Tasks tile shows the tasks due today and the overdue ones, and opens Tasks`() {
        show(TodaySamples.withTasks)
        assertShows("Tasks", "3", "2 overdue")
        shoot("today-tasks")
        compose.onNodeWithText("2 overdue").performClick()
        assertEquals(listOf<TodayDestination>(TodayDestination.Tasks()), opened)
    }

    @Test
    fun `the bell counts the items that need attention, and goes to them (T69)`() {
        show()
        compose.onNodeWithContentDescription("2 items need attention").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
    }

    @Test
    fun `without items the bell is off`() {
        show(TodaySamples.complete.copy(cards = TodaySamples.complete.cards.filter { it.type == TodayCardType.Tile }))
        compose.onNodeWithContentDescription("Nothing needs attention").assertIsNotEnabled()
    }

    @Test
    fun `hidden amounts hide every amount`() {
        Money.hidden = { true }
        show()
        compose.onAllNodesWithText("$186.89").assertCountEquals(0)
        compose.onAllNodesWithText("$7,750.00").assertCountEquals(0)
        assertShows(Money.HIDDEN, "${Money.HIDDEN} over a usual month by now", "NETFLIX went up from ${Money.HIDDEN} to ${Money.HIDDEN}")
        shoot("today-hidden")
    }

    @Test
    fun `tiles and items open their pages, and a tile without a link opens nothing`() {
        show()
        compose.onAllNodesWithText("Net worth").onFirst().performClick()
        compose.onNodeWithText("Budgets").performClick()
        compose.onNodeWithText("Spent this month").performClick()
        compose.onNodeWithText("NETFLIX went up from $16.49 to $18.99").performClick()
        compose.onNodeWithText("4 transactions are in Other because no rule names their merchants").performClick()
        compose.onNodeWithText("Open Money").performClick()

        assertEquals(
            listOf(MoneyLink.NetWorth, MoneyLink.Budgets, MoneyLink.Subscriptions, MoneyLink.Rules).map { TodayDestination.Money(it) },
            opened,
        )
        assertEquals(listOf("money"), openedModules.map { it.id })
    }
}
