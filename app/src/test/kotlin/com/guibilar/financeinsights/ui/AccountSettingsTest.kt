package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
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
class AccountSettingsTest {
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
