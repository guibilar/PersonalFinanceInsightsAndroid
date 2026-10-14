package com.guibilar.financeinsights.core.today

import com.guibilar.financeinsights.AlertSamples
import com.guibilar.financeinsights.TodaySamples
import com.guibilar.financeinsights.core.api.CookieStorage
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.createApiClient
import com.guibilar.financeinsights.core.logic.DataChanges
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlertsApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): AlertsApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    /** As Hestia.Api writes it (T15): enums as names, `type` on each alert, and values that this app does not know. */
    private val answer = """
        {"checkedAt":"2026-10-03T15:00:00+00:00",
         "modules":[{"module":"Money","order":0,"status":"Partial"},{"module":"Documents","order":1,"status":"Sleeping"}],
         "alerts":[
          {"id":"money.budget-over.b1","alertId":"0199c1a2-0000-7000-8000-000000000001","module":"Money","kind":"budget-over",
           "title":"Budget over its limit","text":"{budget} is over its limit ({used} used)",
           "values":{"budget":{"type":"Text","number":null,"text":"Groceries","date":null},
                     "used":{"type":"Percent","number":112,"text":null,"date":null}},
           "severity":"Bad","link":"/money/budgets","raisedAt":"2026-10-01T12:00:00+00:00",
           "updatedAt":"2026-10-02T12:00:00+00:00","type":"Attention"},
          {"id":"documents.expiring.d1","alertId":"0199c1a2-0000-7000-8000-000000000002","module":"Documents","kind":"expiring",
           "title":"Expiring soon","text":"{title} expires on {day}",
           "values":{"title":{"type":"Text","number":null,"text":"Lease","date":null},
                     "day":{"type":"Date","number":null,"text":null,"date":"2026-10-20"}},
           "severity":"Urgent","link":"/documents/d1","raisedAt":"2026-10-02T08:00:00+00:00",
           "updatedAt":"2026-10-02T08:00:00+00:00","type":"Attention","somethingNew":true}
         ]}
    """.trimIndent()

    @Test
    fun `reads the alerts, and turns values it does not know into the defaults`() = runTest {
        server.enqueue(MockResponse.Builder().body(answer).build())

        val alerts = api().alerts()

        assertEquals("/api/alerts", server.takeRequest().url.encodedPath)
        assertEquals(listOf(TodayModuleStatus.Partial, TodayModuleStatus.Complete), alerts.modules.map { it.status })
        val (budget, document) = alerts.alerts
        assertEquals("0199c1a2-0000-7000-8000-000000000001", budget.alertId)
        assertEquals(TodaySeverity.Bad, budget.severity)
        assertEquals("Groceries is over its limit (112% used)", budget.filledText)
        assertEquals("Lease expires on Oct 20, 2026", document.filledText)
        assertEquals(TodaySeverity.Neutral, document.severity)
    }

    @Test
    fun `asks for a fresh check only when told to`() = runTest {
        repeat(2) { server.enqueue(MockResponse.Builder().body("""{"modules":[],"alerts":[]}""").build()) }

        api().alerts(refresh = true)
        api().alerts()

        val first = server.takeRequest().url
        assertEquals("/api/alerts", first.encodedPath)
        assertEquals("true", first.queryParameter("refresh"))
        assertNull(server.takeRequest().url.queryParameter("refresh"))
    }

    @Test
    fun `one module's alerts keep the server's order, with its status`() {
        val money = AlertSamples.complete.of("Money")
        assertEquals(TodayModuleStatus.Complete, money.status)
        assertEquals(listOf(TodaySamples.priceChanged.id, TodaySamples.unsorted.id), money.items.map { it.id })

        assertEquals(TodayModuleStatus.Partial, AlertSamples.partial.of("Money").status)
        assertEquals(TodayModuleStatus.Failed, AlertSamples.failed.of("Money").status)
        assertEquals(emptyList<AlertDto>(), AlertSamples.failed.of("Money").items)
    }

    @Test
    fun `a module that the server does not list is complete, with no alerts`() {
        val garden = AlertSamples.complete.of("Garden")
        assertEquals(ModuleAlerts(TodayModuleStatus.Complete, emptyList()), garden)
    }

    @Test
    fun `the Money alerts read the same as the Money items on Today`() {
        val today = TodaySamples.complete.cards.filter { it.type == TodayCardType.Attention && it.module == "Money" }
        val alerts = AlertSamples.complete.of("Money").items

        assertEquals(today.map { it.filledText }, alerts.map { it.filledText })
        assertEquals(today.map { todayDestination(it.link) }, alerts.map { todayDestination(it.link) })
    }

    @Test
    fun `a change in the app makes the alerts stale until a fresh check`() {
        AlertFreshness.checked(DataChanges.version)
        assertFalse(AlertFreshness.stale)

        val before = DataChanges.version
        DataChanges.changed()
        assertTrue(AlertFreshness.stale)

        // A check that started before the change does not count for it.
        AlertFreshness.checked(before)
        assertTrue(AlertFreshness.stale)
        AlertFreshness.checked(DataChanges.version)
        assertFalse(AlertFreshness.stale)
    }
}
