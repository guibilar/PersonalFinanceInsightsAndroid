package com.guibilar.financeinsights.core.model

import kotlinx.serialization.Serializable

// The sign-in models (/api/Auth/*). They are the same for every module, so they live with the HTTP client.

@Serializable
data class LoginInput(
    val username: String,
    val password: String,
    /** Keeps the session for 14 days of inactivity, like "Remember me" on the web. */
    val rememberMe: Boolean = true,
)

@Serializable
data class ChangePasswordInput(val currentPassword: String, val newPassword: String)

/**
 * The signed-in user. [scope] is `full` or `read-only` (T60): a read-only user can only read, and the screens hide
 * their write actions. An older server sends no scope: that counts as `full`.
 */
@Serializable
data class CurrentUser(val username: String, val scope: String = FULL_SCOPE) {
    /** False only for the `read-only` scope. */
    val canWrite: Boolean get() = scope != READ_ONLY_SCOPE

    companion object {
        const val FULL_SCOPE = "full"
        const val READ_ONLY_SCOPE = "read-only"
    }
}

@Serializable
data class SignedOutSessions(val count: Int)
