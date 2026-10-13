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

@Serializable
data class CurrentUser(val username: String)

@Serializable
data class SignedOutSessions(val count: Int)
