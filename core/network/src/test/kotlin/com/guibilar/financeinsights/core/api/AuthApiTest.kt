package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.CurrentUser
import com.guibilar.financeinsights.core.model.LoginInput
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

/** Signing in, and the one signed-in client that every module's API shares. */
class AuthApiTest {
    private val server = MockWebServer()
    private var stored: String? = null
    private val storage = object : CookieStorage {
        override fun read() = stored
        override fun write(value: String?) {
            stored = value
        }
    }
    private var unauthorized = 0

    /** A module's API, as a new module makes it: an interface next to its screens. */
    interface NotesApi {
        @GET("Notes/count")
        suspend fun count(): Int
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun client(jar: PersistentCookieJar = PersistentCookieJar(storage)) =
        createApiClient(server.url("/").toString().trimEnd('/'), jar, onUnauthorized = { unauthorized++ })

    @Test
    fun `keeps the session cookie across launches and sends it back`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Set-Cookie", "finance_session=abc; path=/; max-age=1209600; httponly; samesite=strict")
                .body("""{"username":"dev"}""").build(),
        )
        server.enqueue(MockResponse.Builder().body("""{"username":"dev"}""").build())

        assertEquals("dev", client().auth.login(LoginInput("Dev", "pw")).username)
        // A new jar reading the same storage: the app after a restart.
        client(PersistentCookieJar(storage)).auth.me()

        val login = server.takeRequest()
        assertEquals("/api/Auth/login", login.url.encodedPath)
        // 14 days of inactivity, like "Remember me" on the web.
        assertTrue(login.body!!.utf8().contains("\"rememberMe\":true"))
        assertEquals("finance_session=abc", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun `reads the scope of the user, and takes a missing one as full`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"username":"read-only token","scope":"read-only"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"username":"dev","scope":"full"}""").build())
        server.enqueue(MockResponse.Builder().body("""{"username":"dev"}""").build())

        assertFalse(client().auth.me().canWrite)
        assertTrue(client().auth.me().canWrite)
        // An older server sends no scope (T60).
        assertEquals(CurrentUser("dev", "full"), client().auth.me())
    }

    @Test
    fun `a signed-out cookie is forgotten`() = runTest {
        val jar = PersistentCookieJar(storage)
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "s=abc; path=/; max-age=100").body("""{"username":"dev"}""").build())
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "s=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT").code(204).build())
        client(jar).auth.login(LoginInput("dev", "pw"))

        client(jar).auth.logout()

        assertTrue(jar.isEmpty)
        assertEquals(null, stored)
        assertEquals("/api/Auth/login", server.takeRequest().url.encodedPath)
        assertEquals("/api/Auth/logout", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `reports a 401 outside sign-in as the session ending`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"title":"Sign-in failed","detail":"Incorrect username or password."}""").build())
        server.enqueue(MockResponse.Builder().code(401).build())

        val me = apiCall { client().auth.me() }.apiError!!
        val login = apiCall { client().auth.login(LoginInput("dev", "nope")) }.apiError!!
        val module = apiCall { client().api<NotesApi>().count() }.apiError!!

        assertTrue(me.isUnauthorized && module.isUnauthorized)
        assertEquals("Incorrect username or password.", login.message)
        assertEquals(2, unauthorized)
    }

    @Test
    fun `a module's API uses the signed-in client's cookie`() = runTest {
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "s=abc; path=/; max-age=100").body("""{"username":"dev"}""").build())
        server.enqueue(MockResponse.Builder().body("7").build())
        val client = client()

        client.auth.login(LoginInput("dev", "pw"))
        assertEquals(7, client.api<NotesApi>().count())

        server.takeRequest()
        val count = server.takeRequest()
        assertEquals("/api/Notes/count", count.url.encodedPath)
        assertEquals("s=abc", count.headers["Cookie"])
    }

    @Test
    fun `each API is made once per client`() {
        val client = client()
        assertSame(client.api<NotesApi>(), client.api<NotesApi>())
        assertSame(client.auth, client.api<AuthApi>())
    }
}
