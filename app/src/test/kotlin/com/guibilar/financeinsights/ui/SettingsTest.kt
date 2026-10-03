package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.AccountUpdate
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.StatementFormat
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.settings.AccountsViewModel
import com.guibilar.financeinsights.ui.settings.CategoriesViewModel
import com.guibilar.financeinsights.ui.settings.RulesViewModel
import com.guibilar.financeinsights.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.messages(model: SettingsViewModel): List<UiMessage> {
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.messages.collect { messages += it } }
        return messages
    }

    @Test
    fun `a move while searching passes the rule you can see, not the hidden ones`() = runTest(dispatcher) {
        val model = RulesViewModel(api)
        advanceUntilIdle()
        val rules = (model.state.value.rules as LoadState.Loaded).value
        // Two rules far apart, as a search would show them.
        val visible = listOf(rules[2], rules[40])

        assertNull(model.movePosition(visible, visible[0], -1))
        model.move(visible, visible[1], -1)
        advanceUntilIdle()

        assertEquals("moveCategoryRule" to (rules[40].id to 2), api.sent.single())
        assertEquals(rules[40].id, (model.state.value.rules as LoadState.Loaded).value[2].id)
    }

    @Test
    fun `a rule that exists already is explained by the API`() = runTest(dispatcher) {
        val model = RulesViewModel(api)
        api.refusing["addCategoryRule"] = FakeFinanceApi.conflict("A rule for \"NO FRILLS\" already exists.")

        assertEquals("A rule for \"NO FRILLS\" already exists.", model.saveRule(null, CategoryRuleInput("no frills", "c1", 0)))
    }

    @Test
    fun `deleting a category moves what used it, and says where`() = runTest(dispatcher) {
        val model = CategoriesViewModel(api)
        val messages = messages(model)
        advanceUntilIdle()
        val categories = model.state.value.valueOrNull!!
        val dining = categories.first { it.name == "Dining" }
        val other = categories.first { it.isFallback }

        model.delete(dining, other)
        advanceUntilIdle()

        assertEquals("deleteCategory" to (dining.id to other.id), api.sent.single())
        assertEquals("Deleted “Dining”; what used it is now in “Other”", messages.single().text)
    }

    @Test
    fun `an account with transactions after all is explained, and the list reloads`() = runTest(dispatcher) {
        val model = AccountsViewModel(api)
        val messages = messages(model)
        advanceUntilIdle()
        val account = model.state.value.valueOrNull!!.first()
        api.refusing["deleteAccount"] = FakeFinanceApi.conflict("\"Chequing\" has 3 transactions (deleted ones included); only an empty account can be deleted.")

        model.delete(account)
        advanceUntilIdle()

        assertEquals("\"Chequing\" has 3 transactions (deleted ones included); only an empty account can be deleted.", messages.single().text)
        assertNull(model.update(account, AccountUpdate("Chequing", StatementFormat.Neo)))
    }

    @Test
    fun `a wrong current password is shown on its field`() = runTest(dispatcher) {
        val model = AccountSettingsViewModel(api)
        val messages = messages(model)
        api.refusing["changePassword"] = ApiError.Http(400, "Invalid", null, null, mapOf("CurrentPassword" to listOf("Incorrect password.")))

        assertEquals(mapOf("currentPassword" to "Incorrect password."), model.changePassword("nope", "a long new password"))
        api.refusing.clear()
        assertNull(model.changePassword("right", "a long new password"))
        model.signOutOthers()
        advanceUntilIdle()

        assertEquals(
            listOf("Password changed. Anywhere else you were signed in has been signed out.", "Signed out 2 other sessions."),
            messages.map { it.text },
        )
    }
}
