package com.guibilar.financeinsights.data

import android.content.Context
import androidx.core.content.edit
import com.guibilar.financeinsights.core.api.CookieStorage

/** What the app remembers between launches: the server's address and its session cookie, in private storage. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var server: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) = prefs.edit { putString(KEY_SERVER, value) }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit { putString(KEY_USERNAME, value) }

    val cookies = object : CookieStorage {
        override fun read(): String? = prefs.getString(KEY_COOKIES, null)

        // Committed at once: the cookie is what keeps the person signed in if the app is closed right after.
        override fun write(value: String?) {
            prefs.edit(commit = true) { putString(KEY_COOKIES, value) }
        }
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_USERNAME = "username"
        const val KEY_COOKIES = "cookies"
    }
}
