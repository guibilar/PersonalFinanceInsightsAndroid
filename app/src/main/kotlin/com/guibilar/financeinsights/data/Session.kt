package com.guibilar.financeinsights.data

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.ServerAddress
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.createFinanceApi
import com.guibilar.financeinsights.core.model.LoginInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    /** Checking a stored session with the server. */
    data object Checking : AuthState
    data class SignedOut(
        /** Why, when it wasn't the person's choice ("Your session ended"). */
        val reason: String? = null,
    ) : AuthState
    data class SignedIn(val username: String, val api: FinanceApi) : AuthState
}

/** Who is signed in to which server, and the API client for it. */
class Session(
    private val store: SessionStore,
    private val createApi: (server: String, jar: PersistentCookieJar, onUnauthorized: () -> Unit) -> FinanceApi =
        { server, jar, onUnauthorized -> createFinanceApi(server, jar, onUnauthorized) },
) {
    private val jar = PersistentCookieJar(store.cookies)
    private val _state = MutableStateFlow<AuthState>(if (canResume()) AuthState.Checking else AuthState.SignedOut())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val server: String? get() = store.server
    val lastUsername: String? get() = store.username

    private fun canResume() = store.server != null && !jar.isEmpty

    private fun api(server: String) = createApi(server, jar) { sessionEnded() }

    /** At launch: a stored session that the server still accepts signs in without asking. */
    suspend fun resume() {
        val server = store.server
        if (server == null || jar.isEmpty) {
            _state.value = AuthState.SignedOut()
            return
        }
        val api = api(server)
        val me = apiCall { api.me() }
        _state.value = me.fold(
            onSuccess = { AuthState.SignedIn(it.username, api) },
            onFailure = { error ->
                // Unreachable (Tailscale off) isn't signed out: try again on the next launch, with the cookie kept.
                if ((error as ApiError).isUnauthorized) jar.clear()
                AuthState.SignedOut(if (error.isUnauthorized) "Your session ended. Please sign in again." else error.message)
            },
        )
    }

    /** Signs in; the failure's message says why (wrong password, too many attempts, unreachable). */
    suspend fun signIn(serverInput: String, username: String, password: String): Result<Unit> {
        val server = ServerAddress.normalize(serverInput)
            ?: return Result.failure(IllegalArgumentException("Enter the server's address, like https://finance.example.ts.net"))
        if (server != store.server) jar.clear()
        val api = api(server)
        return apiCall { api.login(LoginInput(username.trim(), password)) }.map { user ->
            store.server = server
            store.username = user.username
            _state.value = AuthState.SignedIn(user.username, api)
        }
    }

    suspend fun signOut() {
        (_state.value as? AuthState.SignedIn)?.api?.let { api -> apiCall { api.logout() } }
        jar.clear()
        _state.value = AuthState.SignedOut()
    }

    /** A call answered 401: the session ended elsewhere (signed out, password changed, expired). */
    private fun sessionEnded() {
        if (_state.value is AuthState.SignedIn) {
            jar.clear()
            _state.value = AuthState.SignedOut("Your session ended. Please sign in again.")
        }
    }
}
