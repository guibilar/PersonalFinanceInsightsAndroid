package com.guibilar.financeinsights.core.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The address of the server the web app is served from, e.g. "https://finance.tail1234.ts.net": the API is under
 * its /api path. Typed by hand, so it is forgiving: the scheme defaults to https, and a trailing slash or /api is
 * dropped.
 */
object ServerAddress {
    /** The server's base address ("https://host[:port]"), or null when it isn't a web address. */
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "https://$text"
        val url = text.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        val path = url.encodedPath.trimEnd('/').removeSuffix("/api").trimEnd('/')
        return url.newBuilder().encodedPath(if (path.isEmpty()) "/" else "$path/").query(null).fragment(null).build()
            .toString().trimEnd('/')
    }

    /** The API's base URL for Retrofit, with its trailing slash. */
    fun apiBase(server: String): HttpUrl = "$server/api/".toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Not a server address: $server")
}
