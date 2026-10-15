package com.guibilar.financeinsights.core.people

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

class PeopleApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): PeopleApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** As Hestia.Api writes a PersonDto: null fields written out, and a field this app does not know. */
    private val person = """
        {"person":{"id":"p1","name":"Marco Silva","nickname":null,"relationship":"Friend","groups":["gaming group"],
          "birthday":{"month":11,"day":2,"year":1990},"nextBirthday":"2026-11-02","email":null,"phone":null,
          "contactEveryDays":42,"lastContactOn":"2026-08-28","contactDueOn":"2026-10-09","contactOverdue":true,
          "createdAt":"2026-01-10T10:00:00+00:00","updatedAt":"2026-08-28T10:00:00+00:00"},
         "notes":null,
         "interactions":[{"id":"c1","personId":"p1","occurredOn":"2026-08-28","kind":"Coffee","note":null,"createdAt":"x"}],
         "interactionCount":1,
         "giftIdeas":[{"id":"g1","personId":"p1","title":"Kettle","notes":null,"price":49.5,"occasion":null,"givenOn":null,"createdAt":"x"}],
         "loans":[{"id":"l1","personId":"p1","personName":"Marco Silva","itemName":"Stove","direction":"Borrowed",
                   "lentOn":"2026-07-04","dueOn":null,"returnedOn":null,"overdue":false,"notes":null,"createdAt":"x"}],
         "links":[],
         "somethingNew":true}
    """.trimIndent()

    @Test
    fun `reads a person with the records`() = runTest {
        answer(person)

        val read = api().person("p1")

        assertEquals("/api/people/p1", server.takeRequest().url.encodedPath)
        assertEquals(Birthday(11, 2, 1990), read.person.birthday)
        assertTrue(read.person.contactOverdue)
        assertEquals("Coffee", read.interactions.single().kind)
        assertEquals(49.5, read.giftIdeas.single().price!!, 0.0)
        assertEquals(LoanDirection.Borrowed, read.loans.single().direction)
    }

    @Test
    fun `sends a new loan and the list filters`() = runTest {
        answer("""{"id":"l2","personId":"p1","itemName":"Drill","direction":"Lent","lentOn":"2026-10-14"}""")
        answer("""{"items":[],"page":1,"pageSize":100,"totalCount":0}""")

        api().addLoan("p1", LoanDetails("Drill", dueOn = "2026-11-01"))
        api().people(group = "family", sort = "contact", pageSize = 100)

        val add = server.takeRequest()
        assertEquals("/api/people/p1/loans", add.url.encodedPath)
        val body = add.body!!.utf8()
        assertTrue(body, body.contains("\"itemName\":\"Drill\"") && body.contains("\"dueOn\":\"2026-11-01\""))
        val list = server.takeRequest().url
        assertEquals("family", list.queryParameter("group"))
        assertEquals("contact", list.queryParameter("sort"))
        assertEquals("100", list.queryParameter("pageSize"))
    }
}
