package com.guibilar.financeinsights.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn

/**
 * Quick capture: four buttons, as in the design. "Photo" and "Scan" add a document (the photo picker, or the
 * document scanner); "Expense" opens the "Add transaction" form. Voice comes with the Inbox module. For a read-only
 * user ([canWrite] false, T60), every button is disabled and the note says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    contentPadding: PaddingValues,
    onExpense: () -> Unit,
    onScan: () -> Unit = {},
    onPhoto: () -> Unit = {},
    canWrite: Boolean = true,
) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText("Capture", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        ReadableColumn {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Add something in a few taps.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureButton(Icons.Outlined.PhotoCamera, "Photo", enabled = canWrite, Modifier.weight(1f), onPhoto)
                    CaptureButton(Icons.Outlined.DocumentScanner, "Scan", enabled = canWrite, Modifier.weight(1f), onScan)
                    CaptureButton(Icons.Outlined.Mic, "Voice", enabled = false, Modifier.weight(1f)) {}
                    CaptureButton(Icons.Outlined.Payments, "Expense", enabled = canWrite, Modifier.weight(1f), onExpense)
                }
                Text(
                    if (canWrite) {
                        "Photo and Scan add a document. Voice is coming soon."
                    } else {
                        "This account can only read. It cannot add anything."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A tall button: an icon over its label. A disabled one is dimmed. */
@Composable
private fun CaptureButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(88.dp).semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
    }
}

/** A top-level page that comes in a later version: its name, what it will do, and a short note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(title: String, icon: ImageVector, message: String, contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(title = { FitText(title, fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) })
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "Coming soon",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
