package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.AuthApi
import com.guibilar.financeinsights.core.model.ChangePasswordInput
import com.guibilar.financeinsights.core.model.CurrentUser
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.core.model.SignedOutSessions
import java.io.IOException

/** Signing in, always as "dev". Each call is recorded, and any can be made to fail. */
class FakeAuthApi : AuthApi {
    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()
    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()
    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()
    /** The scope that sign-in and `me` answer (T60). */
    var scope: String = CurrentUser.FULL_SCOPE

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    override suspend fun login(login: LoginInput) = call("login", login) { CurrentUser("dev", scope) }
    override suspend fun logout() = call("logout", null) {}
    override suspend fun me() = call("me", null) { CurrentUser("dev", scope) }
    override suspend fun changePassword(change: ChangePasswordInput) = call("changePassword", change) {}
    override suspend fun signOutOthers() = call("signOutOthers", null) { SignedOutSessions(2) }
}

/**
 * A signed-in client on fakes: each module's API is the fake that implements it. Without a [FakeAuthApi] among
 * [apis], it gets a new one.
 */
fun fakeApiClient(vararg apis: Any): ApiClient {
    val all = if (apis.any { it is AuthApi }) apis.toList() else apis.toList() + FakeAuthApi()
    return ApiClient { type -> all.firstOrNull(type::isInstance) ?: error("No fake for ${type.simpleName}") }
}
