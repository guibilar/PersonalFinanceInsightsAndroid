package com.guibilar.financeinsights.core.home

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

class HomeApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): HomeApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** As Hestia.Api writes an ItemDto: amounts as numbers, null fields written out, and a field this app does not know. */
    private val item = """
        {"id":"0199d1a2-0000-7000-8000-000000000001","name":"Dishwasher","roomId":"0199d1a2-0000-7000-8000-0000000000r1",
         "roomName":"Kitchen","category":"Appliance","brand":"Bosch","model":"SHX78","serialNumber":null,"quantity":2,
         "purchasedOn":"2024-11-02","purchasePrice":899.99,"currentValue":null,"value":1799.98,"warrantyUntil":"2026-10-20",
         "notes":null,"tags":["kitchen"],"createdAt":"2026-09-20T10:00:00+00:00","updatedAt":"2026-09-20T10:00:00+00:00",
         "links":[{"id":"l1","kind":"Documents.Document","targetId":"d2","createdAt":"2026-09-20T10:01:00+00:00"}],
         "documents":[{"id":"d2","title":"Dishwasher warranty","kind":"Warranty","expiresOn":"2026-11-02"}],
         "maintenance":[{"id":"t20","title":"Clean the filter","status":"Open","dueOn":"2026-10-25","repeatEvery":1,
                         "repeatUnit":"Months","lastDoneAt":null}],
         "somethingNew":true}
    """.trimIndent()

    @Test
    fun `reads an item with its links, documents and maintenance`() = runTest {
        answer(item)

        val read = api().item("0199d1a2-0000-7000-8000-000000000001")

        assertEquals("/api/home/items/0199d1a2-0000-7000-8000-000000000001", server.takeRequest().url.encodedPath)
        assertEquals("Kitchen", read.roomName)
        assertEquals(2, read.quantity)
        assertEquals(899.99, read.purchasePrice!!, 0.0)
        assertNull(read.currentValue)
        assertEquals(1799.98, read.value, 0.0)
        assertEquals(ItemLinkKinds.DOCUMENT, read.links.single().kind)
        assertEquals("Warranty", read.documents.single().kind)
        assertEquals("Months", read.maintenance.single().repeatUnit)
        assertEquals(read.summary.value, read.value, 0.0)
    }

    @Test
    fun `reads the summary with the entry for the items without a room`() = runTest {
        answer(
            """{"date":"2026-10-03","itemCount":3,"totalValue":2549.98,"warrantiesEnding":1,
               "rooms":[{"roomId":"r1","name":"Kitchen","itemCount":2,"totalValue":2400.0},
                        {"roomId":"r2","name":"Office","itemCount":0,"totalValue":0},
                        {"roomId":null,"name":null,"itemCount":1,"totalValue":149.98}]}""",
        )

        val summary = api().summary()

        assertEquals("/api/home/summary", server.takeRequest().url.encodedPath)
        assertEquals(3, summary.itemCount)
        assertEquals(listOf("Kitchen", "Office", null), summary.rooms.map { it.name })
        assertNull(summary.rooms.last().roomId)
        assertEquals(1, summary.warrantiesEnding)
    }

    @Test
    fun `lists a page with the filters it is given, and leaves the others out`() = runTest {
        answer("""{"items":[],"page":1,"pageSize":50,"totalCount":0,"totalPages":0,"hasPreviousPage":false,"hasNextPage":false}""")

        api().items(room = NO_ROOM, q = "bosch", warranty = WarrantyFilter.EXPIRING, pageSize = 50)

        val url = server.takeRequest().url
        assertEquals("/api/home/items", url.encodedPath)
        assertEquals("none", url.queryParameter("room"))
        assertEquals("bosch", url.queryParameter("q"))
        assertEquals("expiring", url.queryParameter("warranty"))
        assertEquals("50", url.queryParameter("pageSize"))
        assertNull(url.queryParameter("category"))
        assertNull(url.queryParameter("tag"))
    }

    @Test
    fun `adds an item with its links, and leaves the empty values out`() = runTest {
        answer(item, 201)

        api().create(NewItem(name = "Dishwasher", purchasePrice = 899.99, links = listOf(NewItemLink.transaction("x1"))))

        val request = server.takeRequest()
        assertEquals("POST /api/home/items", "${request.method} ${request.url.encodedPath}")
        assertEquals(
            """{"name":"Dishwasher","purchasePrice":899.99,"tags":[],"links":[{"kind":"Money.Transaction","targetId":"x1"}]}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `saves all the details`() = runTest {
        answer(item)

        api().update("i1", ItemDraft(name = "Dishwasher", quantity = "2", purchasePrice = "899.99", tags = "Kitchen").toDetails())

        val request = server.takeRequest()
        assertEquals("PUT /api/home/items/i1", "${request.method} ${request.url.encodedPath}")
        assertEquals("""{"name":"Dishwasher","quantity":2,"purchasePrice":899.99,"tags":["kitchen"]}""", request.body?.utf8())
    }

    @Test
    fun `rooms, links, delete, by-link and the export`() = runTest {
        answer("""[{"id":"r1","name":"Kitchen","sortOrder":1,"itemCount":2,"totalValue":2400.5}]""")
        answer("""{"id":"r9","name":"Garage","sortOrder":5,"itemCount":0,"totalValue":0}""", 201)
        answer("""{"id":"r9","name":"Shed","sortOrder":5,"itemCount":0,"totalValue":0}""")
        answer("", 204)
        answer("""{"id":"l2","kind":"Tasks.Task","targetId":"t1","createdAt":"2026-10-03T12:00:00Z"}""", 201)
        answer("", 204)
        answer("", 204)
        answer("""[{"id":"i1","name":"Dishwasher","quantity":1,"value":899.99}]""")
        answer("﻿Room,Name\r\nKitchen,Dishwasher\r\n")

        val api = api()
        val rooms = api.rooms()
        api.createRoom(RoomDetails("Garage"))
        api.updateRoom("r9", RoomDetails("Shed"))
        api.deleteRoom("r9")
        val link = api.addLink("i1", NewItemLink.task("t1"))
        api.removeLink("i1", "l2")
        api.delete("i1")
        val linked = api.byLink(ItemLinkKinds.DOCUMENT, "d2")
        val csv = api.export(room = "r1").string()

        assertEquals("/api/home/rooms", server.takeRequest().url.encodedPath)
        server.takeRequest().let {
            assertEquals("POST /api/home/rooms", "${it.method} ${it.url.encodedPath}")
            assertEquals("""{"name":"Garage"}""", it.body?.utf8())
        }
        server.takeRequest().let { assertEquals("PUT /api/home/rooms/r9", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("DELETE /api/home/rooms/r9", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let {
            assertEquals("POST /api/home/items/i1/links", "${it.method} ${it.url.encodedPath}")
            assertEquals("""{"kind":"Tasks.Task","targetId":"t1"}""", it.body?.utf8())
        }
        server.takeRequest().let { assertEquals("DELETE /api/home/items/i1/links/l2", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("DELETE /api/home/items/i1", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().url.let {
            assertEquals("/api/home/items/by-link", it.encodedPath)
            assertEquals("Documents.Document", it.queryParameter("kind"))
            assertEquals("d2", it.queryParameter("id"))
        }
        server.takeRequest().url.let {
            assertEquals("/api/home/items/export.csv", it.encodedPath)
            assertEquals("r1", it.queryParameter("room"))
        }
        assertEquals(2400.5, rooms.single().totalValue, 0.0)
        assertEquals("l2", link.id)
        assertEquals("Dishwasher", linked.single().name)
        assertTrue(linked.single().links.isEmpty())
        assertTrue(csv.contains("Kitchen,Dishwasher"))
    }
}
