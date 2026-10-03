package com.guibilar.financeinsights.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Where the cookies are kept between launches (the app keeps them in its private storage). */
interface CookieStorage {
    fun read(): String?
    fun write(value: String?)
}

/**
 * Keeps the API's session cookie, so the app stays signed in between launches as a browser does. Session cookies
 * (no expiry) are kept too: the app is the "browser" and it isn't closed by being swiped away.
 */
class PersistentCookieJar(private val storage: CookieStorage, private val now: () -> Long = System::currentTimeMillis) : CookieJar {
    private val lock = Any()
    private var cookies: MutableList<Cookie> = load()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            for (cookie in cookies) {
                this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                // An expiry in the past is how a server deletes a cookie (signing out).
                if (cookie.expiresAt > now()) this.cookies.add(cookie)
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val (expired, live) = cookies.partition { it.expiresAt <= now() }
        if (expired.isNotEmpty()) {
            cookies = live.toMutableList()
            persist()
        }
        live.filter { it.matches(url) }
    }

    /** Forgets every cookie: signing out, or switching servers. */
    fun clear() = synchronized(lock) {
        cookies.clear()
        persist()
    }

    val isEmpty get() = synchronized(lock) { cookies.none { it.expiresAt > now() } }

    private fun persist() {
        storage.write(if (cookies.isEmpty()) null else json.encodeToString(cookies.map { Stored.of(it) }))
    }

    private fun load(): MutableList<Cookie> {
        val text = storage.read() ?: return mutableListOf()
        return runCatching { json.decodeFromString<List<Stored>>(text).mapNotNull { it.toCookie() } }
            .getOrDefault(emptyList()).toMutableList()
    }

    @Serializable
    private data class Stored(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean,
    ) {
        fun toCookie(): Cookie? = runCatching {
            Cookie.Builder().name(name).value(value).path(path).apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (persistent) expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }.build()
        }.getOrNull()

        companion object {
            fun of(cookie: Cookie) = Stored(
                cookie.name, cookie.value, cookie.domain, cookie.path, cookie.expiresAt,
                cookie.secure, cookie.httpOnly, cookie.hostOnly, cookie.persistent,
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
