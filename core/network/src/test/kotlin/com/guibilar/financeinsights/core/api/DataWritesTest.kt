package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.logic.DataChanges
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** T58: a successful write through the client says that the data changed; reads and failures do not. */
class DataWritesTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(DataWrites).build()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun call(method: String, path: String, code: Int = 200): Long {
        server.enqueue(MockResponse.Builder().code(code).body("{}").build())
        val before = DataChanges.version
        val body = if (method == "GET") null else "{}".toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url(server.url(path)).method(method, body).build()).execute().close()
        return DataChanges.version - before
    }

    @Test
    fun `writes change the data, reads and read-only queries do not`() {
        assertEquals(1, call("POST", "/api/Transaction"))
        assertEquals(1, call("PUT", "/api/Transaction/abc"))
        assertEquals(1, call("DELETE", "/api/Budget/1"))
        assertEquals(1, call("POST", "/api/tasks/t1/done"))
        assertEquals(0, call("GET", "/api/Transaction"))
        assertEquals(0, call("POST", "/api/Transaction/Filter"))
        assertEquals(0, call("POST", "/api/Reports/breakdown"))
        assertEquals(0, call("POST", "/api/Auth/login"))
    }

    @Test
    fun `a failed write changes nothing`() {
        assertEquals(0, call("POST", "/api/Transaction", code = 400))
        assertEquals(0, call("DELETE", "/api/Budget/1", code = 500))
    }

    @Test
    fun `the read-only list is finance's`() {
        assertTrue(DataWrites.isWrite("PATCH", "/api/Category/1"))
        assertFalse(DataWrites.isWrite("POST", "/api/CategoryRules/Preview"))
        assertFalse(DataWrites.isWrite("POST", "/api/Budget/Filter"))
        assertFalse(DataWrites.isWrite("POST", "/api/Reports/merchants"))
    }
}
