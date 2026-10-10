package com.guibilar.financeinsights.data

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.PersistentCookieJar
import com.guibilar.financeinsights.core.api.ServerAddress
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.createFinanceApi
import com.guibilar.financeinsights.core.model.LoginInput
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface AuthState {
    /** Checking a stored session with the server. */
    data object Checking : AuthState
    data class SignedOut(
        /** Why, when it wasn't the person's choice ("Your session ended"). */
        val reason: String? = null,
    ) : AuthState
    /** The stored session couldn't be checked (Tailscale off, server down): kept, to try again. */
    data class Unreachable(val message: String) : AuthState
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

    /**
     * At launch (and on Retry): a stored session that the server still accepts signs in without asking. An unreachable
     * server isn't signed out — the cookie is kept and the person can try again once Tailscale is on.
     */
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
                if ((error as ApiError).isUnauthorized) {
                    jar.clear()
                    AuthState.SignedOut("Your session ended. Please sign in again.")
                } else {
                    AuthState.Unreachable(error.message ?: "Could not reach the server.")
                }
            },
        )
    }

    /** From the unreachable screen: sign in by hand instead (another server, or another account). */
    fun signInInstead() {
        if (_state.value is AuthState.Unreachable) _state.value = AuthState.SignedOut()
    }

    /**
     * The API for work in the background (budget alerts, the widget): the signed-in client, or one on the stored
     * session when the app isn't open. Null when signed out. A 401 here doesn't sign out: the app finds out on open.
     */
    fun backgroundApi(): FinanceApi? {
        (_state.value as? AuthState.SignedIn)?.let { return it.api }
        val server = store.server ?: return null
        if (jar.isEmpty) return null
        // One client per server, reused: each run of the widget or the alerts used to build its own (and its threads).
        background?.takeIf { it.first == server }?.let { return it.second }
        return createApi(server, jar) {}.also { background = server to it }
    }
    @Volatile private var background: Pair<String, FinanceApi>? = null

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
        // To the end even when the screen that asked goes away meanwhile (Back, another tab): cancelled halfway, the
        // phone would stay signed in, cookie and all, after the person chose to sign out.
        withContext(NonCancellable) {
            (_state.value as? AuthState.SignedIn)?.api?.let { api -> apiCall { api.logout() } }
            jar.clear()
            _state.value = AuthState.SignedOut()
        }
    }

    /** A call answered 401: the session ended elsewhere (signed out, password changed, expired). */
    private fun sessionEnded() {
        if (_state.value is AuthState.SignedIn) {
            jar.clear()
            _state.value = AuthState.SignedOut("Your session ended. Please sign in again.")
        }
    }
}
