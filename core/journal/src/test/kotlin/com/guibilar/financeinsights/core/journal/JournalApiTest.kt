package com.guibilar.financeinsights.core.journal

import com.guibilar.financeinsights.core.api.CookieStorage
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.createApiClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class JournalApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): JournalApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `reads the summary as the server writes it`() = runTest {
        answer(
            """
            {"date":"2026-10-10","streak":6,"week":[{"day":"2026-10-04","written":true},{"day":"2026-10-10","written":false}],
             "today":null,
             "onThisDay":[{"yearsAgo":1,"entry":{"id":"e1","day":"2025-10-10","excerpt":"Moved the desk.","length":15,
               "updatedAt":"2025-10-10T20:00:00+00:00"}}],
             "entryCount":8,"aFieldThisAppDoesNotKnow":1}
            """.trimIndent(),
        )

        val summary = api().summary()

        assertEquals("/api/journal/summary", server.takeRequest().target)
        assertEquals(6, summary.streak)
        assertNull(summary.today)
        assertEquals(listOf(true, false), summary.week.map { it.written })
        assertEquals("Moved the desk.", summary.onThisDay.single().entry.excerpt)
    }

    @Test
    fun `saves the whole text of a day, and searches`() = runTest {
        answer("""{"id":"e2","day":"2026-10-10","text":"A walk.","createdAt":"x","updatedAt":"x"}""")
        answer("""{"items":[],"page":1,"pageSize":50,"totalCount":0,"totalPages":1,"hasPreviousPage":false,"hasNextPage":false}""")

        val saved = api().save("2026-10-10", JournalText("A walk."))
        val save = server.takeRequest()
        api().entries(q = "river walk", pageSize = 50)
        val search = server.takeRequest()

        assertEquals("A walk.", saved.text)
        assertEquals(("PUT" to "/api/journal/entries/2026-10-10"), save.method to save.target)
        assertEquals("""{"text":"A walk."}""", save.body!!.utf8())
        assertEquals("/api/journal/entries?q=river%20walk&page=1&pageSize=50", search.target)
    }
}
