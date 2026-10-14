package com.guibilar.financeinsights.core.tasks

import com.guibilar.financeinsights.core.api.CookieStorage
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.createApiClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TasksApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): TasksApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** As Hestia.Api writes a TaskDto: enums as names, null fields written out, and a field this app does not know. */
    private val task = """
        {"id":"0199c1a2-0000-7000-8000-000000000001","title":"Water the plants","notes":null,"dueOn":"2026-10-06",
         "tags":["home"],"status":"Open","doneAt":null,
         "repeat":{"every":4,"unit":"Days","mode":"FromCompletion"},"sortOrder":null,
         "lastDoneAt":"2026-10-02T08:00:00+00:00","createdAt":"2026-09-20T10:00:00+00:00","updatedAt":"2026-10-02T08:00:00+00:00",
         "links":[{"id":"0199c1a2-0000-7000-8000-0000000000a1","kind":"Documents.Document","targetId":"d1",
                   "createdAt":"2026-09-20T10:01:00+00:00"}],
         "completions":[{"id":"c1","doneAt":"2026-10-02T08:00:00+00:00","dueOn":"2026-10-01"},
                        {"id":"c0","doneAt":"2026-09-27T09:30:00+00:00","dueOn":null}],
         "somethingNew":true}
    """.trimIndent()

    @Test
    fun `reads a task with its repeat, links and completions`() = runTest {
        answer(task)

        val read = api().task("0199c1a2-0000-7000-8000-000000000001")

        assertEquals("/api/tasks/0199c1a2-0000-7000-8000-000000000001", server.takeRequest().url.encodedPath)
        assertEquals(TaskStatus.Open, read.status)
        assertEquals(TaskRepeat(4, RepeatUnit.Days, RepeatMode.FromCompletion), read.repeat)
        assertEquals("2026-10-06", read.dueOn)
        assertNull(read.notes)
        assertEquals(TaskLinkKinds.DOCUMENT, read.links.single().kind)
        assertEquals("d1", read.links.single().targetId)
        assertEquals(listOf("2026-10-01", null), read.completions.map { it.dueOn })
    }

    @Test
    fun `values from a newer server become the defaults`() = runTest {
        answer("""{"id":"t1","title":"x","status":"Archived","repeat":{"every":2,"unit":"Fortnights","mode":"Smart"}}""")

        val read = api().task("t1")

        assertEquals(TaskStatus.Open, read.status)
        assertEquals(TaskRepeat(2, RepeatUnit.Days, RepeatMode.FromDueDate), read.repeat)
    }

    @Test
    fun `lists a page with the filters it is given, and leaves the others out`() = runTest {
        answer("""{"items":[],"page":1,"pageSize":100,"totalCount":0,"totalPages":0,"hasPreviousPage":false,"hasNextPage":false}""")

        api().tasks(status = TaskStatus.Open, due = TaskDue.OVERDUE, pageSize = 100)

        val url = server.takeRequest().url
        assertEquals("/api/tasks", url.encodedPath)
        assertEquals("Open", url.queryParameter("status"))
        assertEquals("overdue", url.queryParameter("due"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("100", url.queryParameter("pageSize"))
        assertNull(url.queryParameter("tag"))
        assertNull(url.queryParameter("q"))
    }

    @Test
    fun `adds a task with its links, and leaves the empty values out`() = runTest {
        answer(task, 201)

        api().create(NewTask(title = "Renew Car insurance", dueOn = "2026-10-11", links = listOf(NewTaskLink.document("d1"))))

        val request = server.takeRequest()
        assertEquals("POST /api/tasks", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            """{"title":"Renew Car insurance","dueOn":"2026-10-11","tags":[],"links":[{"kind":"Documents.Document","targetId":"d1"}]}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `saves all the details with the repeat`() = runTest {
        answer(task)

        api().update("t1", TaskDetails(title = "Water the plants", tags = listOf("home"), repeat = TaskRepeat(4, RepeatUnit.Days, RepeatMode.FromCompletion)))

        val request = server.takeRequest()
        assertEquals("PUT /api/tasks/t1", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            """{"title":"Water the plants","tags":["home"],"repeat":{"every":4,"unit":"Days","mode":"FromCompletion"}}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `done, undo, links, delete, by-link and the summary`() = runTest {
        answer(task)
        answer(task)
        answer("""{"id":"l1","kind":"Money.Transaction","targetId":"x1","createdAt":"2026-10-03T12:00:00Z"}""", 201)
        answer("", 204)
        answer("", 204)
        answer("""[{"id":"t1","title":"Renew the policy","dueOn":"2026-10-11","status":"Open"}]""")
        answer("""{"date":"2026-10-03","dueToday":3,"overdue":1,"upcoming":2}""")

        val api = api()
        api.done("t1")
        api.undo("t1")
        val link = api.addLink("t1", NewTaskLink.transaction("x1"))
        api.removeLink("t1", "l1")
        api.delete("t1")
        val linked = api.byLink(TaskLinkKinds.DOCUMENT, "d1")
        val counts = api.summary()

        server.takeRequest().let { assertEquals("POST /api/tasks/t1/done", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("POST /api/tasks/t1/undo", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let {
            assertEquals("POST /api/tasks/t1/links", "${it.method} ${it.url.encodedPath}")
            assertEquals("""{"kind":"Money.Transaction","targetId":"x1"}""", it.body?.utf8())
        }
        server.takeRequest().let { assertEquals("DELETE /api/tasks/t1/links/l1", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("DELETE /api/tasks/t1", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().url.let {
            assertEquals("/api/tasks/by-link", it.encodedPath)
            assertEquals("Documents.Document", it.queryParameter("kind"))
            assertEquals("d1", it.queryParameter("id"))
        }
        assertEquals("/api/tasks/summary", server.takeRequest().url.encodedPath)
        assertEquals("l1", link.id)
        assertEquals("Renew the policy", linked.single().title)
        assertEquals(TaskCounts("2026-10-03", 3, 1, 2), counts)
        assertTrue(linked.single().links.isEmpty())
    }
}
