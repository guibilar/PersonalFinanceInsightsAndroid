package com.guibilar.financeinsights.core.documents

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

class DocumentsApiTest {
    private val server = MockWebServer()
    private val storage = object : CookieStorage {
        override fun read(): String? = null
        override fun write(value: String?) {}
    }

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun api(): DocumentsApi = createApiClient(server.url("/").toString().trimEnd('/'), PersistentCookieJar(storage)).api()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** As Hestia.Api writes a DocumentDto: enums as names, null fields written out, and a field this app does not know. */
    private val document = """
        {"id":"0199c1a2-0000-7000-8000-000000000001","title":"Kettle receipt","kind":"Receipt","tags":["kitchen"],
         "issuedOn":"2026-10-01","expiresOn":"2028-10-01","notes":null,
         "createdAt":"2026-10-13T16:00:00+00:00","updatedAt":"2026-10-13T16:00:00+00:00",
         "ocrStatus":"Failed","ocrError":"receipt.heic: no text","ocrText":null,
         "files":[{"id":"0199c1a2-0000-7000-8000-0000000000f1","fileName":"receipt.heic","contentType":"image/heic",
                   "size":204800,"sha256":"ab12","addedAt":"2026-10-13T16:00:00+00:00"}],
         "links":[{"id":"0199c1a2-0000-7000-8000-0000000000a1","kind":"Money.Transaction","targetId":"t1",
                   "createdAt":"2026-10-13T16:01:00+00:00"}],
         "somethingNew":true}
    """.trimIndent()

    @Test
    fun `reads a document with its files and links`() = runTest {
        answer(document)

        val read = api().document("0199c1a2-0000-7000-8000-000000000001")

        assertEquals("/api/documents/0199c1a2-0000-7000-8000-000000000001", server.takeRequest().url.encodedPath)
        assertEquals(DocumentKind.Receipt, read.kind)
        assertEquals(OcrStatus.Failed, read.ocrStatus)
        assertEquals("receipt.heic: no text", read.ocrError)
        assertNull(read.ocrText)
        assertEquals("2028-10-01", read.expiresOn)
        assertTrue(read.files.single().isImage)
        assertEquals(204800L, read.files.single().size)
        assertEquals("t1", read.transactionLinks.single().targetId)
    }

    @Test
    fun `values from a newer server become the defaults`() = runTest {
        answer("""{"id":"d1","title":"x","kind":"Passport","ocrStatus":"Queued","tags":[],"files":[],"links":[]}""")

        val read = api().document("d1")

        assertEquals(DocumentKind.Other, read.kind)
        assertEquals(OcrStatus.Unknown, read.ocrStatus)
    }

    @Test
    fun `lists a page with the filters it is given, and leaves the others out`() = runTest {
        answer("""{"items":[],"page":2,"pageSize":20,"totalCount":21,"totalPages":2,"hasPreviousPage":true,"hasNextPage":false}""")

        val page = api().documents(kind = DocumentKind.Warranty, q = "dish washer", expiresFrom = "2026-10-03", expiresTo = "2026-11-02", page = 2)

        val url = server.takeRequest().url
        assertEquals("/api/documents", url.encodedPath)
        assertEquals("Warranty", url.queryParameter("kind"))
        assertEquals("dish washer", url.queryParameter("q"))
        assertEquals("2026-10-03", url.queryParameter("expiresFrom"))
        assertEquals("2026-11-02", url.queryParameter("expiresTo"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("pageSize"))
        assertNull(url.queryParameter("tag"))
        assertEquals(21, page.totalCount)
    }

    @Test
    fun `saves all the details, and leaves the empty ones out`() = runTest {
        answer(document)

        api().update("d1", DocumentDetails(title = "Kettle", kind = DocumentKind.Warranty, tags = listOf("kitchen"), expiresOn = "2028-10-01"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/documents/d1", request.url.encodedPath)
        assertEquals(
            """{"title":"Kettle","kind":"Warranty","tags":["kitchen"],"expiresOn":"2028-10-01"}""",
            request.body?.utf8(),
        )
    }

    @Test
    fun `links, unlinks, retries the OCR and deletes`() = runTest {
        answer("""{"id":"l1","kind":"Money.Transaction","targetId":"t1","createdAt":"2026-10-13T16:01:00Z"}""", 201)
        answer("", 204)
        answer(document, 202)
        answer("", 204)
        answer("""[{"id":"d1","title":"Kettle receipt","kind":"Receipt"}]""")

        val api = api()
        val link = api.addLink("d1", NewDocumentLink.transaction("t1"))
        api.removeLink("d1", "l1")
        api.retryOcr("d1")
        api.delete("d1")
        val linked = api.byLink(DocumentLinkKinds.MONEY_TRANSACTION, "t1")

        assertEquals("l1", link.id)
        val add = server.takeRequest()
        assertEquals("POST /api/documents/d1/links", "${add.method} ${add.url.encodedPath}")
        assertEquals("""{"kind":"Money.Transaction","targetId":"t1"}""", add.body?.utf8())
        server.takeRequest().let { assertEquals("DELETE /api/documents/d1/links/l1", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("POST /api/documents/d1/ocr", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().let { assertEquals("DELETE /api/documents/d1", "${it.method} ${it.url.encodedPath}") }
        server.takeRequest().url.let {
            assertEquals("/api/documents/by-link", it.encodedPath)
            assertEquals("Money.Transaction", it.queryParameter("kind"))
            assertEquals("t1", it.queryParameter("id"))
        }
        assertEquals("Kettle receipt", linked.single().title)
    }

    @Test
    fun `uploads the files and the details as one form`() = runTest {
        answer(document, 201)
        val pdf = "%PDF-1.7 test".toByteArray()

        api().upload(
            DocumentUpload(
                files = listOf(UploadFile("scan.pdf", pdf)),
                title = " Kettle receipt ",
                kind = DocumentKind.Receipt,
                tags = listOf("kitchen", " appliance ", "kitchen", ""),
                issuedOn = "2026-10-01",
                expiresOn = null,
                notes = "  ",
            ).toMultipart(),
        )

        val request = server.takeRequest()
        assertEquals("POST /api/documents", "${request.method} ${request.url.encodedPath}")
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
        val body = request.body!!.utf8()
        val parts = body.split("--" + request.headers["Content-Type"]!!.substringAfter("boundary=")).drop(1).dropLast(1)
        val fields = parts.map { part ->
            val name = Regex("name=\"([^\"]*)\"").find(part)!!.groupValues[1]
            val value = part.substringAfter("\r\n\r\n").removeSuffix("\r\n")
            name to value
        }
        assertEquals(
            listOf(
                "files" to "%PDF-1.7 test",
                "title" to "Kettle receipt",
                "kind" to "Receipt",
                "tags" to "kitchen",
                "tags" to "appliance",
                "issuedOn" to "2026-10-01",
            ),
            fields,
        )
        assertTrue(parts.first().contains("filename=\"scan.pdf\""))
        assertTrue(parts.first().contains("Content-Type: application/pdf"))
    }
}
