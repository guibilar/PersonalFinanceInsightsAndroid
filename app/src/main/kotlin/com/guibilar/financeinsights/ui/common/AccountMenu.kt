package com.guibilar.financeinsights.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.guibilar.financeinsights.ui.AccountInfo
import kotlinx.coroutines.launch

/** Who is signed in, to which server, and signing out. */
@Composable
fun AccountMenuButton(account: AccountInfo, onSignOut: suspend () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.AccountCircle, "Account") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(account.username, style = MaterialTheme.typography.titleSmall)
                    Text(account.server.removePrefix("https://"), style = MaterialTheme.typography.bodySmall)
                }
            },
            onClick = {},
            enabled = false,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Sign out") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            onClick = {
                open = false
                scope.launch { onSignOut() }
            },
        )
    }
}
