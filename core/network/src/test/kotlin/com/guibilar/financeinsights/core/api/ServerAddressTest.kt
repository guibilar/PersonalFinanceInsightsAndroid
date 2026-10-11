package com.guibilar.financeinsights.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `accepts an address typed loosely`() {
        assertEquals("https://finance.tail1234.ts.net", ServerAddress.normalize(" finance.tail1234.ts.net "))
        assertEquals("https://finance.tail1234.ts.net", ServerAddress.normalize("https://finance.tail1234.ts.net/"))
        assertEquals("https://finance.tail1234.ts.net", ServerAddress.normalize("https://finance.tail1234.ts.net/api/"))
        assertEquals("http://192.168.1.20:8080", ServerAddress.normalize("http://192.168.1.20:8080"))
        assertEquals("https://host/finance", ServerAddress.normalize("https://host/finance/api"))
    }

    @Test
    fun `refuses what isn't a web address`() {
        assertNull(ServerAddress.normalize(""))
        assertNull(ServerAddress.normalize("ftp://host"))
        assertNull(ServerAddress.normalize("not an address"))
    }

    @Test
    fun `the API lives under api`() {
        assertEquals("https://host/finance/api/", ServerAddress.apiBase("https://host/finance").toString())
    }
}
