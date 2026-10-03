package com.guibilar.financeinsights.core.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** The JSON the API speaks: camelCase names, enums as their names; fields it adds later are skipped. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    // An enum value this app doesn't know yet becomes the field's default instead of failing the whole response.
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/** Builds the API client for a server. [onUnauthorized] runs when a call other than signing in answers 401. */
fun createFinanceApi(
    server: String,
    cookieJar: PersistentCookieJar,
    onUnauthorized: () -> Unit = {},
    configure: OkHttpClient.Builder.() -> Unit = {},
): FinanceApi {
    val sessionEnded = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401 && !chain.request().url.encodedPath.endsWith("/Auth/login")) onUnauthorized()
        response
    }
    val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(sessionEnded)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .apply(configure)
        .build()
    return Retrofit.Builder()
        .baseUrl(ServerAddress.apiBase(server))
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(FinanceApi::class.java)
}
