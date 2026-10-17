package com.guibilar.financeinsights.data

import androidx.test.core.app.ApplicationProvider
import com.guibilar.financeinsights.FakeAuthApi
import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.model.CurrentUser
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.fakeApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionTest {
    private val auth = FakeAuthApi()
    private val finance = FakeFinanceApi()
    private val client = fakeApiClient(auth, finance)
    private val store = SessionStore(ApplicationProvider.getApplicationContext()).apply {
        server = SERVER
        username = "dev"
        val url = SERVER.toHttpUrl()
        PersistentCookieJar(cookies).saveFromResponse(
            url,
            listOf(Cookie.Builder().domain(url.host).name("auth").value("x").expiresAt(Long.MAX_VALUE).build()),
        )
    }
    private val session = Session(store) { _, _, _ -> client }

    private fun cookieKept() = !PersistentCookieJar(store.cookies).isEmpty

    @Test
    fun `an unreachable server at launch keeps the session, to try again`() = runTest {
        auth.failing += "me"
        session.resume()
        assertTrue(session.state.value is AuthState.Unreachable)
        assertTrue(cookieKept())

        auth.failing.clear()
        session.resume()
        assertEquals("dev", (session.state.value as AuthState.SignedIn).username)
    }

    @Test
    fun `the scope of the user says if the screens can write`() = runTest {
        session.resume()
        assertTrue((session.state.value as AuthState.SignedIn).canWrite)

        auth.scope = CurrentUser.READ_ONLY_SCOPE
        session.resume()
        assertFalse((session.state.value as AuthState.SignedIn).canWrite)

        session.signIn(SERVER, "dev", "a password")
        assertFalse((session.state.value as AuthState.SignedIn).canWrite)
    }

    @Test
    fun `a session the server refuses asks to sign in again`() = runTest {
        auth.refusing["me"] = ApiError.Http(401, "Unauthorized", null, null, emptyMap())
        session.resume()
        assertEquals("Your session ended. Please sign in again.", (session.state.value as AuthState.SignedOut).reason)
        assertFalse(cookieKept())
    }

    @Test
    fun `work in the background reuses one client`() {
        var built = 0
        val session = Session(store) { _, _, _ -> built++; client }
        session.backgroundClient()
        session.backgroundClient()
        assertEquals(1, built)
    }

    @Test
    fun `signing in instead leaves the unreachable screen`() = runTest {
        auth.failing += "me"
        session.resume()
        session.signInInstead()
        assertEquals(AuthState.SignedOut(), session.state.value)
    }

    @Test
    fun `signing in gives one client for sign-in and every module's API`() = runTest {
        val result = session.signIn(SERVER, " dev ", "a password")

        assertTrue(result.isSuccess)
        assertEquals("login" to LoginInput("dev", "a password"), auth.sent.single())
        val signedIn = session.state.value as AuthState.SignedIn
        assertSame(finance, signedIn.client.api<FinanceApi>())
        assertSame(signedIn.client, session.backgroundClient())
    }

    @Test
    fun `signing out tells the server and forgets the cookie`() = runTest {
        session.signIn(SERVER, "dev", "a password")

        session.signOut()

        assertEquals("logout", auth.sent.last().first)
        assertFalse(cookieKept())
        assertEquals(AuthState.SignedOut(), session.state.value)
    }

    @Test
    fun `a 401 of an earlier session's client does not sign out the new one`() = runTest {
        val unauthorized = mutableListOf<() -> Unit>()
        val session = Session(store) { _, _, onUnauthorized ->
            unauthorized.add(onUnauthorized)
            fakeApiClient(auth, finance)
        }
        session.signIn(SERVER, "dev", "a password")
        session.signOut()
        session.signIn(SERVER, "dev", "a password")

        // A slow call of the first client comes back refused.
        unauthorized.first().invoke()
        assertEquals("dev", (session.state.value as AuthState.SignedIn).username)

        // One of the signed-in client still ends the session.
        unauthorized.last().invoke()
        assertEquals("Your session ended. Please sign in again.", (session.state.value as AuthState.SignedOut).reason)
    }

    private companion object {
        const val SERVER = "https://finance.example.ts.net"
    }
}
