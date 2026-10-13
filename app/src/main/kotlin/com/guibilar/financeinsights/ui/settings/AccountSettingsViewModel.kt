package com.guibilar.financeinsights.ui.settings

import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.AuthApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.logic.passwordErrors
import com.guibilar.financeinsights.core.model.ChangePasswordInput
import kotlinx.coroutines.launch

/** The signed-in account: its password, and the other places it is signed in. */
class AccountSettingsViewModel(private val api: AuthApi) : SettingsViewModel() {
    /** Null when changed, or the reasons by field ("currentPassword", "newPassword", "other"). */
    suspend fun changePassword(current: String, new: String): Map<String, String>? {
        val result = apiCall { api.changePassword(ChangePasswordInput(current, new)) }
        val error = result.apiError ?: run {
            say("Password changed. Anywhere else you were signed in has been signed out.")
            return null
        }
        return passwordErrors(error)
    }

    fun signOutOthers() = viewModelScope.launch {
        val result = apiCall { api.signOutOthers() }
        say(
            result.getOrNull()?.count?.let { count ->
                if (count == 0) "You weren't signed in anywhere else." else "Signed out $count other ${if (count == 1) "session" else "sessions"}."
            } ?: result.apiError!!.loadMessage("Could not sign out the other sessions."),
        )
    }
}
