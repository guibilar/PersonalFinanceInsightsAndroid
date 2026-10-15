package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.TagReference
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.ui.settings.TagsScreen
import com.guibilar.financeinsights.ui.settings.TagsViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import com.guibilar.financeinsights.ui.transactions.TransactionsScreen
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The Tags screen, and a transaction's note and tags in the list and the edit sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xxhdpi")
class TagsScreensTest {
    @get:Rule val compose = createComposeRule()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 4)
    private val account = AccountInfo("dev", "https://finance.example.ts.net")
    private val vacation = TagReference("01a10460-5d8c-7d55-a0b8-a21750ee834e", "Vacation 2026")

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FinanceTheme(ripples = false, darkTheme = false, dynamicColor = false) { Surface { content() } } }
        compose.waitForIdle()
    }

    private fun shoot(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test
    fun tags() {
        var opened: Tag? = null
        val model = TagsViewModel(api)
        show { TagsScreen(model, PaddingValues(), onBack = {}, openTransactions = { opened = it }) }

        compose.onNodeWithText("5 transactions").assertExists()
        shoot("tags")

        compose.onNodeWithText("Vacation 2026").performClick()
        compose.waitForIdle()
        shoot("tag-sheet")
        compose.onNodeWithText("Show its 5 transactions").performClick()
        assertEquals("Vacation 2026", opened?.name)
    }

    @Test
    fun noteAndTags() {
        // The first two rows: one with tags and a note, one with only a note.
        api.decorate = {
            when (it.description) {
                "METRO 221" -> it.copy(note = "Party supplies", tags = listOf(vacation, TagReference("x", "Tax deductible")))
                "SHOPPERS DRUG MART" -> it.copy(note = "Sunscreen for the trip")
                else -> it
            }
        }
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        show { TransactionsScreen(model, account, {}, PaddingValues()) }
        // A row reads as one sentence to TalkBack, its tags and note included.
        compose.onNode(hasContentDescription("Tagged Vacation 2026, Tax deductible, Note: Party supplies", substring = true)).assertExists()
        compose.onNode(hasContentDescription("Note: Sunscreen for the trip", substring = true)).assertExists()
        shoot("transactions-tags")

        compose.onNode(hasContentDescription("METRO 221", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("#Vacation 2026").performScrollTo().assertExists()
        compose.onNode(hasSetTextAction() and hasText("Add a tag")).performScrollTo().performTextInput("Gifts")
        compose.onNodeWithText("Create").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("#Gifts").performScrollTo().assertExists()
        shoot("edit-tags")

        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitForIdle()
        val (_, update) = api.sent.single { it.first == "updateTransaction" }.second as Pair<*, *>
        assertEquals(setOf(vacation.id, "x", "new-tag"), (update as TransactionUpdate).tagIds?.toSet())
        assertEquals(null, update.note)
    }
}
