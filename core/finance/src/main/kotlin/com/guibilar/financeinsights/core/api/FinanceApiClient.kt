package com.guibilar.financeinsights.core.api

import okhttp3.OkHttpClient

/** Builds the API client for a server. [onUnauthorized] runs when a call other than signing in answers 401. */
fun createFinanceApi(
    server: String,
    cookieJar: PersistentCookieJar,
    onUnauthorized: () -> Unit = {},
    configure: OkHttpClient.Builder.() -> Unit = {},
): FinanceApi = createApiClient(server, cookieJar, onUnauthorized, configure).create(FinanceApi::class.java)
