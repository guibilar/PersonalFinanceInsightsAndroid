package com.guibilar.financeinsights.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.guibilar.financeinsights.FakeAuthApi
import com.guibilar.financeinsights.ui.settings.AccountSettingsScreen
import com.guibilar.financeinsights.ui.settings.AccountSettingsViewModel
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignOutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `signing out of this phone asks first`() {
        var signedOut = 0
        val model = AccountSettingsViewModel(FakeAuthApi())
        compose.setContent {
            FinanceTheme(dynamicColor = false) {
                Surface { AccountSettingsScreen(model, AccountInfo("dev", "https://finance.example.ts.net"), { signedOut++ }, PaddingValues()) {} }
            }
        }
        compose.onNodeWithText("Sign out of this phone").performScrollTo().performClick()
        compose.onNodeWithText("Sign out of this phone?").assertExists()
        assertEquals(0, signedOut)
        // The dialog's button.
        compose.onAllNodesWithText("Sign out").onLast().performClick()
        compose.waitForIdle()
        assertEquals(1, signedOut)
    }
}
