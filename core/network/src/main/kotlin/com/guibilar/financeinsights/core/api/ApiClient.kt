package com.guibilar.financeinsights.core.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The JSON the API speaks: camelCase names, enums as their names; fields it adds later are skipped. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    // An enum value this app doesn't know yet becomes the field's default instead of failing the whole response.
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * The signed-in client for one server: one HTTP client, with the session cookie, that every module's API shares.
 * A module makes its Retrofit interface from it in one line: `client.api<TodayApi>()`. Each interface is made once
 * and kept, so the same client always gives the same object.
 *
 * Tests make one from fakes: `ApiClient { type -> fakes.first(type::isInstance) }`.
 */
class ApiClient(private val create: (Class<*>) -> Any) {
    private val apis = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> api(type: Class<T>): T = type.cast(apis.getOrPut(type) { create(type) })

    inline fun <reified T : Any> api(): T = api(T::class.java)

    /** Signing in and the account. */
    val auth: AuthApi get() = api()
}

/**
 * Builds the client for a server's API, with the session cookie. [onUnauthorized] runs when a call other than
 * signing in answers 401.
 */
fun createApiClient(
    server: String,
    cookieJar: PersistentCookieJar,
    onUnauthorized: () -> Unit = {},
    configure: OkHttpClient.Builder.() -> Unit = {},
): ApiClient {
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
    val retrofit = Retrofit.Builder()
        .baseUrl(ServerAddress.apiBase(server))
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
    return ApiClient { type -> retrofit.create(type) as Any }
}
