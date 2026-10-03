package com.guibilar.financeinsights.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

/**
 * Why a call failed, as the app tells it: the API's problem details (RFC 9457) when it answered, or that the server
 * couldn't be reached.
 */
sealed class ApiError(message: String) : Exception(message) {
    /** No answer: offline, Tailscale off, or the wrong address. */
    class Unreachable(cause: IOException) : ApiError("Can't reach the server. Check your connection and that Tailscale is on.") {
        init {
            initCause(cause)
        }
    }

    /** The API answered with an error status. */
    class Http(
        val status: Int,
        val title: String?,
        val detail: String?,
        /** A machine-readable reason, e.g. "account-mismatch". */
        val code: String?,
        /** For a 400: the reasons per field. */
        val fieldErrors: Map<String, List<String>>,
    ) : ApiError(detail ?: title ?: defaultMessage(status))

    /** Anything else (a response the app couldn't read). */
    class Unexpected(cause: Throwable) : ApiError("Something went wrong. Please try again.") {
        init {
            initCause(cause)
        }
    }

    /** The session ended (signed out elsewhere, or expired). */
    val isUnauthorized get() = this is Http && status == 401

    /** The API's explanation of a conflict (409, e.g. a duplicate), or [fallback] for anything else. */
    fun conflictOr(fallback: String): String =
        if (this is Http && status == 409 && !detail.isNullOrBlank()) detail else fallback

    /** The API's reasons for refusing the input (400), joined, or [fallback] for anything else. */
    fun validationOr(fallback: String): String =
        if (this is Http && status == 400) fieldErrors.values.flatten().filter { it.isNotBlank() }
            .joinToString(" ").ifEmpty { detail ?: fallback } else fallback

    /** What to tell the person when a load failed: unreachable says why, anything else uses [fallback]. */
    fun loadMessage(fallback: String): String = if (this is Unreachable) message!! else fallback

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Turns whatever a call threw into an [ApiError]; cancellation is rethrown, never wrapped. */
        fun from(error: Throwable): ApiError = when (error) {
            is ApiError -> error
            is kotlin.coroutines.cancellation.CancellationException -> throw error
            is HttpException -> fromResponse(error.code(), error.response()?.errorBody()?.string())
            is IOException -> Unreachable(error)
            else -> Unexpected(error)
        }

        fun fromResponse(status: Int, body: String?): Http {
            val problem = body?.takeIf { it.isNotBlank() }?.let {
                runCatching { json.decodeFromString<Problem>(it) }.getOrNull()
            }
            return Http(
                status = status,
                title = problem?.title,
                detail = problem?.detail,
                code = problem?.code,
                fieldErrors = problem?.errors.orEmpty().mapValues { (_, value) -> messages(value) },
            )
        }

        private fun messages(value: JsonElement): List<String> = when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(value.contentOrNull)
            else -> emptyList()
        }
    }

    @Serializable
    private data class Problem(
        val title: String? = null,
        val detail: String? = null,
        val code: String? = null,
        val errors: Map<String, JsonElement>? = null,
    )
}

/** Runs an API call, turning its failure into an [ApiError]. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(ApiError.from(error))
    }

/** The failure as an [ApiError] (every failure from [apiCall] is one). */
val Result<*>.apiError: ApiError? get() = exceptionOrNull() as? ApiError

/**
 * What a status without the API's explanation means to the person. 502–504 come from the proxy in front of the
 * API (Tailscale serve, nginx) when the API itself is down or restarting.
 */
private fun defaultMessage(status: Int) = when (status) {
    502, 503, 504 -> "The server isn't answering right now ($status). It may be restarting: try again in a moment."
    in 500..599 -> "The server had a problem ($status). Try again in a moment."
    else -> "The server answered $status."
}
