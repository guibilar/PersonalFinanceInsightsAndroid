package com.guibilar.financeinsights.core.today

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

class TodayApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): TodayApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    /** As Hestia.Api writes it: enums as names, null fields written out, and a field this app does not know. */
    private val answer = """
        {"date":"2026-10-03","generatedAt":"2026-10-03T15:00:00+00:00",
         "modules":[{"module":"Money","status":"Partial"},{"module":"Garden","status":"Sleeping"}],
         "cards":[
          {"id":"money.net-worth","module":"Money","type":"Tile","kind":"net-worth","title":"Net worth",
           "value":{"type":"Money","number":7750.00,"text":null,"date":null},
           "text":"+{change} since last month","values":{"change":{"type":"Money","number":1950,"text":null,"date":null}},
           "severity":"Good","trend":"Up","link":"/money/net-worth","somethingNew":1},
          {"id":"money.statements-behind.a1","module":"Money","type":"Attention","kind":"statements-behind",
           "title":"Statement to import","value":null,"text":"{account}: statements reach {reach}. Import last month's",
           "values":{"account":{"type":"Text","number":null,"text":"Visa","date":null},
                     "reach":{"type":"Date","number":null,"text":null,"date":"2026-08-31"}},
           "severity":"Urgent","trend":"Sideways","link":"/money/transactions"},
          {"id":"garden.weeds","module":"Garden","type":"Banner","kind":"weeds","title":"Weeds","value":null,
           "text":null,"values":{},"severity":"Neutral","trend":null,"link":null}
         ]}
    """.trimIndent()

    @Test
    fun `reads the cards, and turns values it does not know into the defaults`() = runTest {
        server.enqueue(MockResponse.Builder().body(answer).build())

        val today = api().today()

        assertEquals("2026-10-03", today.date)
        assertEquals(listOf(TodayModuleStatus.Partial, TodayModuleStatus.Complete), today.modules.map { it.status })
        val (netWorth, behind, unknown) = today.cards
        assertEquals(TodayCardType.Tile, netWorth.type)
        assertEquals("$7,750.00", netWorth.valueText)
        assertEquals("+$1,950.00 since last month", netWorth.filledText)
        assertEquals(TodayTrend.Up, netWorth.trend)
        assertEquals("Visa: statements reach Aug 31, 2026. Import last month's", behind.filledText)
        assertEquals(TodaySeverity.Neutral, behind.severity)
        assertNull(behind.trend)
        assertEquals(TodayCardType.Unknown, unknown.type)
    }

    @Test
    fun `asks for a new answer only with refresh`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().body("""{"date":"2026-10-03","modules":[],"cards":[]}""").build()) }

        api().today()
        api().today(refresh = true)

        val first = server.takeRequest().url
        assertEquals("/api/today", first.encodedPath)
        assertNull(first.queryParameter("refresh"))
        assertEquals("true", server.takeRequest().url.queryParameter("refresh"))
    }
}
