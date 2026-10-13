package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.Flow
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

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    // Signing in and the session cookie are tested with AuthApi, in :core:network.
    private fun api(jar: PersistentCookieJar = PersistentCookieJar(storage)): FinanceApi =
        createApiClient(server.url("/").toString().trimEnd('/'), jar).api()

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
    fun `uploads a Neo PDF statement as a PDF, and a CSV as CSV`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().body("""{"read":1,"added":1,"skipped":0}""").build()) }

        api().importStatement("statement-july-31-2026.pdf", "%PDF-1.7 ...".toByteArray(), "a1")
        api().importStatement("export.csv", "2026-09-30,COFFEE,4.50,".toByteArray(), "a1")

        val pdf = server.takeRequest().body!!.utf8()
        val csv = server.takeRequest().body!!.utf8()
        assertTrue(pdf, pdf.contains("filename=\"statement-july-31-2026.pdf\"") && pdf.contains("Content-Type: application/pdf"))
        assertTrue(csv, csv.contains("Content-Type: text/csv"))
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

    @Test
    fun `a proxy's bad gateway says the server isn't answering, not just a number`() {
        val gateway = ApiError.fromResponse(502, "<html>Bad Gateway</html>")
        assertTrue(gateway.message!!.startsWith("The server isn't answering right now (502)"))
        assertEquals("The server answered 418.", ApiError.fromResponse(418, null).message)
    }
}
