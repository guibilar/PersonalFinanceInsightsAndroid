package com.guibilar.financeinsights.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.ui.common.UiMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** What the settings screens share: messages after an action, and running one that says why it failed. */
abstract class SettingsViewModel : ViewModel() {
    // Kept until shown, so a message isn't lost while the screen is away.
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    protected fun say(text: String) {
        _messages.trySend(UiMessage(text))
    }

    /**
     * Runs a change; null when done (after [then]), or what to tell the person: the API's 409 or 400 reason first.
     * It runs to the end even when the sheet that asked is closed or the phone rotates meanwhile (see [outlivingScreen]).
     */
    protected suspend fun change(fallback: String, call: suspend () -> Unit, then: () -> Unit): String? = outlivingScreen {
        val result = apiCall { call() }
        val error = result.apiError
        if (error == null) {
            then()
            null
        } else {
            error.conflictOr(error.validationOr(error.loadMessage(fallback)))
        }
    }

    /**
     * Work a sheet started, in the view model's scope rather than the sheet's: closing the sheet (or rotating) cancels
     * the waiting, not the work, so a rule added is also applied and the list reloaded.
     */
    protected suspend fun <T> outlivingScreen(block: suspend () -> T): T = viewModelScope.async { block() }.await()

    /**
     * Shown again: back on its tab (the More tab keeps its screens), or from a screen opened above it. What changed
     * meanwhile (a rule made from a transaction, a statement imported) shows. The first time is the screen opening,
     * just after the view model loaded it.
     */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reloadOnResume()
    }
    private var shown = false

    /** What [resumed] loads again; nothing by default. */
    protected open fun reloadOnResume() {}
}
