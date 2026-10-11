package com.guibilar.financeinsights.ui.common

import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError

/** Something a screen loads: still coming, failed (with what to tell the person), or here. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val message: String) : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>

    val valueOrNull: T? get() = (this as? Loaded<T>)?.value
}

/** Runs an API call into a [LoadState]; [failure] is what to say when the server answered with an error. */
suspend fun <T> load(failure: String, block: suspend () -> T): LoadState<T> {
    val result = apiCall(block)
    return result.fold(
        onSuccess = { LoadState.Loaded(it) },
        onFailure = { LoadState.Failed(result.apiError?.loadMessage(failure) ?: failure) },
    )
}
