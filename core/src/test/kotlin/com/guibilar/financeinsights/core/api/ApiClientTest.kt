package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.core.model.TransactionFilter
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private val server = MockWebServer()
    private var stored: String? = null
    private val storage = object : CookieStorage {
        override fun read() = stored
        override fun write(value: String?) {
            stored = value
        }
    }
    private var unauthorized = 0

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(jar: PersistentCookieJar = PersistentCookieJar(storage)) =
        createFinanceApi(server.url("/").toString().trimEnd('/'), jar, onUnauthorized = { unauthorized++ })

    @Test
    fun `keeps the session cookie across launches and sends it back`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Set-Cookie", ".AspNetCore.Identity.Application=abc; path=/; max-age=1209600; httponly; samesite=strict")
                .body("""{"username":"dev"}""").build(),
        )
        server.enqueue(MockResponse.Builder().body("""{"username":"dev"}""").build())

        assertEquals("dev", api().login(LoginInput("Dev", "pw")).username)
        // A new jar reading the same storage: the app after a restart.
        api(PersistentCookieJar(storage)).me()

        val login = server.takeRequest()
        assertEquals("/api/Auth/login", login.url.encodedPath)
        assertTrue(login.body!!.utf8().contains("\"rememberMe\":true"))
        assertEquals(".AspNetCore.Identity.Application=abc", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun `a signed-out cookie is forgotten`() = runTest {
        val jar = PersistentCookieJar(storage)
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "s=abc; path=/; max-age=100").body("""{"username":"dev"}""").build())
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "s=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT").code(204).build())
        api(jar).login(LoginInput("dev", "pw"))

        api(jar).logout()

        assertTrue(jar.isEmpty)
        assertEquals(null, stored)
    }

    @Test
    fun `reports a 401 outside sign-in as the session ending`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"title":"Sign-in failed","detail":"Incorrect username or password."}""").build())

        val me = apiCall { api().me() }.apiError!!
        val login = apiCall { api().login(LoginInput("dev", "nope")) }.apiError!!

        assertTrue(me.isUnauthorized)
        assertEquals("Incorrect username or password.", login.message)
        assertEquals(1, unauthorized)
    }

    @Test
    fun `reads transactions, leaving out what it doesn't know`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"items":[{"id":"t1","date":"2026-09-15T00:00:00Z","amount":12.5,"description":"COFFEE",
                "category":{"id":"c1","name":"Dining","color":"#FF0000"},"categoryLocked":false,"transactionType":"Debit",
                "accountId":"a1","accountName":"Visa","accountType":"CreditCard","flow":"Teleport","flowLocked":false,
                "budgetId":null,"budget":null,"somethingNew":1}],"page":1,"pageSize":25,"totalCount":1,"totalPages":1,
                "hasPreviousPage":false,"hasNextPage":false}""",
            ).build(),
        )

        val page = api().transactions(TransactionFilter(flow = Flow.Refund))

        val transaction = page.items.single()
        assertEquals(Flow.Spending, transaction.flow)
        assertEquals("2026-09-15", transaction.day.toString())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body, body.contains("\"flow\":\"Refund\"") && !body.contains("description"))
    }

    @Test
    fun `explains failures the way the web app does`() = runTest {
        server.enqueue(MockResponse.Builder().code(409).body("""{"title":"Conflict","detail":"An account named \"Visa\" already exists."}""").build())
        server.enqueue(MockResponse.Builder().code(400).body("""{"title":"Invalid","errors":{"amount":["Use whole cents."],"date":["A date is required."]}}""").build())

        val conflict = apiCall { api().accounts() }.apiError!!
        val invalid = apiCall { api().accounts() }.apiError!!
        server.close()
        val unreachable = apiCall { api().accounts() }.apiError!!

        assertEquals("An account named \"Visa\" already exists.", conflict.conflictOr("Could not save."))
        assertEquals("Could not save.", invalid.conflictOr("Could not save."))
        assertEquals("Use whole cents. A date is required.", invalid.validationOr("Could not save."))
        assertTrue(unreachable is ApiError.Unreachable)
        assertTrue(unreachable.loadMessage("Could not load.").contains("Tailscale"))
    }
}
