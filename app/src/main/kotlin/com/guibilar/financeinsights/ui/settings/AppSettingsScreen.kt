package com.guibilar.financeinsights.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.guibilar.financeinsights.alerts.BudgetAlerts
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.security.canAuthenticate
import kotlinx.coroutines.flow.emptyFlow

/** The app's own settings, kept on this phone: how it looks, the app lock, and notifications. */
@Composable
fun AppSettingsScreen(preferences: AppPreferences, lock: AppLock?, contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    val theme by preferences.theme.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val locking by preferences.lock.collectAsState()
    val alerts by preferences.budgetAlerts.collectAsState()
    val priceAlerts by preferences.priceAlerts.collectAsState()
    val monthlyReview by preferences.monthlyReview.collectAsState()
    val importReminders by preferences.importReminders.collectAsState()
    val hideWidget by preferences.hideWidgetAmounts.collectAsState()
    // Checked again on coming back, a screen lock just set up in the phone's settings.
    var lockAvailable by remember { mutableStateOf(canAuthenticate(context)) }
    LifecycleResumeEffect(Unit) {
        lockAvailable = canAuthenticate(context)
        onPauseOrDispose {}
    }
    var alertsNote by remember { mutableStateOf<String?>(null) }
    // The notification switched on while the permission was asked for.
    var waiting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    fun apply(set: (Boolean) -> Unit, on: Boolean) {
        set(on)
        BudgetAlerts.reschedule(context, preferences)
    }
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val set = waiting
        waiting = null
        if (granted) {
            alertsNote = null
            set?.let { apply(it, true) }
        } else {
            alertsNote = "Notifications are off for this app. Allow them in the phone's settings to get these."
        }
    }
    /** Turns a notification on (asking for the permission first, if need be) or off. */
    fun switch(set: (Boolean) -> Unit, on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BudgetAlerts.canNotify(context)) {
            waiting = set
            askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            alertsNote = null
            apply(set, on)
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
                val activity = hostActivity as? FragmentActivity
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
            SwitchRow(
                "Hide amounts on the widget",
                "The home-screen widget shows no spending or budgets, as anyone can see the home screen.",
                hideWidget,
            ) { preferences.setHideWidgetAmounts(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Heading("Notifications")
            SwitchRow(
                "Budget alerts",
                "A notification when a budget passes 80% and when it's used up, once each period. Checked every few hours.",
                alerts,
            ) { switch(preferences::setBudgetAlerts, it) }
            SwitchRow(
                "Subscription price changes",
                "A notification when a subscription or bill with a fixed price starts charging a different amount.",
                priceAlerts,
            ) { switch(preferences::setPriceAlerts, it) }
            SwitchRow(
                "Monthly review",
                "Early each month, the month before: what was spent against the month before it, and where most went.",
                monthlyReview,
            ) { switch(preferences::setMonthlyReview, it) }
            SwitchRow(
                "Import reminders",
                "A notification when no statement has been imported for more than a week, and once a week after that until one is.",
                importReminders,
            ) { switch(preferences::setImportReminders, it) }
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
