package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.MIN_PASSWORD_LENGTH
import com.guibilar.financeinsights.ui.AccountInfo
import kotlinx.coroutines.launch

/** Who is signed in where; changing the password; signing out other phones and browsers, or this one. */
@Composable
fun AccountSettingsScreen(
    model: AccountSettingsViewModel,
    account: AccountInfo,
    onSignOut: suspend () -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmOthers by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    SettingsScaffold("Account", onBack, contentPadding, model.messages) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Signed in as ${account.username}", style = MaterialTheme.typography.titleMedium)
            Text(account.server, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            ChangePassword(model)
            HorizontalDivider()
            Text("Sessions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Signs out every other phone and browser signed in to this account, say one you lost. This one stays signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmOthers = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out everywhere else") }
            // Asked first, like its neighbour: a slip meant typing the password again.
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out of this phone") }
        }
    }
    if (confirmSignOut) {
        ConfirmDelete(
            "Sign out of this phone?",
            "You'll need your password to sign in again.",
            onConfirm = { scope.launch { onSignOut() } },
            onDismiss = { confirmSignOut = false },
            confirmLabel = "Sign out",
        )
    }
    if (confirmOthers) {
        ConfirmDelete(
            "Sign out everywhere else?",
            "Every other phone and browser signed in to this account will have to sign in again.",
            onConfirm = { model.signOutOthers() },
            onDismiss = { confirmOthers = false },
            confirmLabel = "Sign out",
        )
    }
}

@Composable
private fun ChangePassword(model: AccountSettingsViewModel) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val errors = buildMap {
        if (current.isEmpty()) put("currentPassword", "Enter your current password.")
        when {
            new.isEmpty() -> put("newPassword", "Enter a new password.")
            new.length < MIN_PASSWORD_LENGTH -> put("newPassword", "At least $MIN_PASSWORD_LENGTH characters.")
        }
        if (confirm != new) put("confirm", "The passwords don't match.")
    }
    fun shown(field: String) = server[field] ?: errors[field]?.takeIf { tried }

    Text("Change password", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "At least $MIN_PASSWORD_LENGTH characters; a few words together is easy to remember. Anywhere else you are signed in is signed out.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    listOf(
        Triple("currentPassword", "Current password", current) to { v: String -> current = v },
        Triple("newPassword", "New password", new) to { v: String -> new = v },
        Triple("confirm", "Repeat the new password", confirm) to { v: String -> confirm = v },
    ).forEach { (field, set) ->
        val (key, label, value) = field
        OutlinedTextField(
            value = value,
            onValueChange = {
                set(it)
                server = server - key
            },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = shown(key) != null,
            supportingText = shown(key)?.let { { Text(it) } },
            // So a password manager fills the current one and offers to save the new one.
            modifier = Modifier.fillMaxWidth().semantics {
                contentType = if (key == "currentPassword") ContentType.Password else ContentType.NewPassword
            },
        )
    }
    server["other"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        enabled = !saving,
        onClick = {
            tried = true
            if (errors.isNotEmpty()) return@Button
            saving = true
            server = emptyMap()
            scope.launch {
                val result = model.changePassword(current, new)
                saving = false
                if (result == null) {
                    current = ""
                    new = ""
                    confirm = ""
                    tried = false
                } else {
                    server = result
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Change password") }
}
