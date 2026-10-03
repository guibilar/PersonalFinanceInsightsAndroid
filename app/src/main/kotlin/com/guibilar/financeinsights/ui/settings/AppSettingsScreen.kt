package com.guibilar.financeinsights.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.guibilar.financeinsights.alerts.BudgetAlerts
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.security.canAuthenticate
import kotlinx.coroutines.flow.emptyFlow

/** The app's own settings, kept on this phone: how it looks, the app lock, and budget alerts. */
@Composable
fun AppSettingsScreen(preferences: AppPreferences, lock: AppLock?, contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme by preferences.theme.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val locking by preferences.lock.collectAsState()
    val alerts by preferences.budgetAlerts.collectAsState()
    val lockAvailable = remember { canAuthenticate(context) }
    var alertsNote by remember { mutableStateOf<String?>(null) }

    fun setAlerts(on: Boolean) {
        preferences.setBudgetAlerts(on)
        if (on) BudgetAlerts.schedule(context) else BudgetAlerts.cancel(context)
    }
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            alertsNote = null
            setAlerts(true)
        } else {
            alertsNote = "Notifications are off for this app. Allow them in the phone's settings to get budget alerts."
        }
    }

    SettingsScaffold("Settings", onBack, contentPadding, emptyFlow()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Heading("Appearance")
            Column(Modifier.selectableGroup()) {
                ThemeChoice.entries.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(theme == choice, role = Role.RadioButton) { preferences.setTheme(choice) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == choice, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(choice.label)
                    }
                }
            }
            // Wallpaper colours exist from Android 12 on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Colours from the wallpaper", "Off: the app's own blue.", dynamicColor) { preferences.setDynamicColor(it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Heading("Security")
            SwitchRow(
                "Lock the app",
                if (lockAvailable) "Ask for a fingerprint, face or the phone's PIN on opening, and after a minute away."
                else "Set up a screen lock on the phone first.",
                locking,
                enabled = lockAvailable,
            ) { on ->
                val activity = context as? FragmentActivity
                if (!on) {
                    preferences.setLock(false)
                    lock?.enabledChanged(false)
                } else if (activity != null) {
                    // Confirmed first, so it can't be turned on by someone who couldn't unlock it.
                    authenticate(activity, "Lock Finance Insights", "Confirm it's you") { ok ->
                        if (ok) {
                            preferences.setLock(true)
                            lock?.enabledChanged(true)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Heading("Notifications")
            SwitchRow(
                "Budget alerts",
                "A notification when a budget passes 80% and when it's used up, once each period. Checked every few hours.",
                alerts,
            ) { on ->
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BudgetAlerts.canNotify(context)) {
                    askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    alertsNote = null
                    setAlerts(on)
                }
            }
            alertsNote?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
