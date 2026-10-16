package com.guibilar.financeinsights.core.calendar

import com.guibilar.financeinsights.core.api.CookieStorage
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.createApiClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalendarApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): CalendarApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `reads a range as the server writes it`() = runTest {
        answer(
            """
            {"today":"2026-10-16","from":"2026-09-27","to":"2026-10-31","timeZone":"America/Edmonton",
             "items":[{"id":"calendar.event.ev1","module":"Calendar","kind":"event","title":"Dentist","text":"Main St 4",
               "allDay":false,"startDate":"2026-10-20","endDate":"2026-10-20","startsAt":"2026-10-20T15:00:00+00:00",
               "endsAt":"2026-10-20T16:00:00+00:00","link":"/calendar?event=ev1","eventId":"ev1","recurring":false,
               "done":null,"aFieldThisAppDoesNotKnow":1}],
             "modules":[{"module":"Calendar","status":"Complete"}]}
            """.trimIndent(),
        )

        val view = api().range("2026-09-27", "2026-10-31")

        assertEquals("/api/calendar?from=2026-09-27&to=2026-10-31", server.takeRequest().target)
        assertEquals("Dentist", view.items.single().title)
        assertEquals("ev1", view.items.single().eventId)
    }

    @Test
    fun `sends an import with its text`() = runTest {
        answer("""{"dryRun":true,"added":1,"updated":0,"unchanged":0,"deleted":0,"skipped":0,"events":[]}""")

        val result = api().import(CalendarImportRequest("BEGIN:VCALENDAR", dryRun = true))

        val request = server.takeRequest()
        assertEquals("/api/calendar/import", request.target)
        assertTrue(request.body!!.utf8().contains("\"dryRun\":true"))
        assertEquals(1, result.added)
    }
}
