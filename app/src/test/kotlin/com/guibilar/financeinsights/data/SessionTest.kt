package com.guibilar.financeinsights.data

import androidx.test.core.app.ApplicationProvider
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionTest {
    private val api = FakeFinanceApi()
    private val store = SessionStore(ApplicationProvider.getApplicationContext()).apply {
        server = SERVER
        username = "dev"
        val url = SERVER.toHttpUrl()
        PersistentCookieJar(cookies).saveFromResponse(
            url,
            listOf(Cookie.Builder().domain(url.host).name("auth").value("x").expiresAt(Long.MAX_VALUE).build()),
        )
    }
    private val session = Session(store) { _, _, _ -> api }

    private fun cookieKept() = !PersistentCookieJar(store.cookies).isEmpty

    @Test
    fun `an unreachable server at launch keeps the session, to try again`() = runTest {
        api.failing += "me"
        session.resume()
        assertTrue(session.state.value is AuthState.Unreachable)
        assertTrue(cookieKept())

        api.failing.clear()
        session.resume()
        assertEquals("dev", (session.state.value as AuthState.SignedIn).username)
    }

    @Test
    fun `a session the server refuses asks to sign in again`() = runTest {
        api.refusing["me"] = ApiError.Http(401, "Unauthorized", null, null, emptyMap())
        session.resume()
        assertEquals("Your session ended. Please sign in again.", (session.state.value as AuthState.SignedOut).reason)
        assertFalse(cookieKept())
    }

    @Test
    fun `signing in instead leaves the unreachable screen`() = runTest {
        api.failing += "me"
        session.resume()
        session.signInInstead()
        assertEquals(AuthState.SignedOut(), session.state.value)
    }

    private companion object {
        const val SERVER = "https://finance.example.ts.net"
    }
}
