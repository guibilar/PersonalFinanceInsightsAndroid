package com.guibilar.financeinsights.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.FakeTodayApi
import com.guibilar.financeinsights.fakeApiClient
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.ui.theme.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Locking hides the account, and unlocking returns to the same place. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h900dp-xxhdpi")
class LockTest {
    @get:Rule val compose = createComposeRule()
    private var now = 0L

    @Test
    fun `unlocking returns to where the app was`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val client = fakeApiClient(FakeFinanceApi(), FakeTodayApi(), FakeDocumentsApi())
        val session = Session(SessionStore(context)) { _, _, _ -> client }
        runBlocking { session.signIn("https://finance.example.ts.net", "dev", "a password") }
        val preferences = AppPreferences(context).apply { setLock(true) }
        val lock = AppLock(preferences) { now }.apply { unlocked() }
        compose.setContent { FinanceTheme(dynamicColor = false) { FinanceRoot(session, lock = lock, preferences = preferences, financePreferences = FinancePreferences(context)) } }
        compose.onNodeWithText("Modules").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertExists()

        // A while away: locked, and nothing of the account shows.
        lock.onBackground(); now += AppLock.GRACE_MS; lock.onForeground()
        compose.waitForIdle()
        compose.onNodeWithText("Finance Insights is locked").assertExists()
        compose.onNodeWithText("Appearance").assertDoesNotExist()

        lock.unlocked()
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertExists()
    }
}
