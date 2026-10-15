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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.guibilar.financeinsights.alerts.Alerts
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.data.AppPreferences
import com.guibilar.financeinsights.data.FinancePreferences
import com.guibilar.financeinsights.data.ThemeChoice
import com.guibilar.financeinsights.notifications.AlertNotificationSettings
import com.guibilar.financeinsights.notifications.AlertNotifications
import com.guibilar.financeinsights.security.AppLock
import com.guibilar.financeinsights.security.authenticate
import com.guibilar.financeinsights.security.canAuthenticate
import kotlinx.coroutines.flow.emptyFlow

/** The app's own settings, kept on this phone: how it looks, the app lock, and notifications. */
@Composable
fun AppSettingsScreen(
    preferences: AppPreferences,
    financePreferences: FinancePreferences,
    lock: AppLock?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    /** The alert notifications (T68); null hides their switches. */
    alertSettings: AlertNotificationSettings? = null,
) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    val theme by preferences.theme.collectAsState()
    val dynamicColor by preferences.dynamicColor.collectAsState()
    val locking by preferences.lock.collectAsState()
    val monthlyReview by financePreferences.monthlyReview.collectAsState()
    val alertsOn = alertSettings?.enabled?.collectAsState()?.value == true
    val moneyAlerts = alertSettings?.money?.collectAsState()?.value == true
    val documentAlerts = alertSettings?.documents?.collectAsState()?.value == true
    val taskAlerts = alertSettings?.tasks?.collectAsState()?.value == true
    val homeAlerts = alertSettings?.home?.collectAsState()?.value == true
    val peopleAlerts = alertSettings?.people?.collectAsState()?.value == true
    val hideWidget by financePreferences.hideWidgetAmounts.collectAsState()
    val hideAmounts by preferences.hideAmounts.collectAsState()
    var alertsNote by remember { mutableStateOf<String?>(null) }
    val blockedNote = "Notifications are off for this app. Allow them in the phone's settings to get these."
    // Checked again on coming back: a screen lock just set up in the phone's settings, or notifications turned off
    // there while a switch here still reads On.
    var lockAvailable by remember { mutableStateOf(canAuthenticate(context)) }
    LifecycleResumeEffect(Unit) {
        lockAvailable = canAuthenticate(context)
        if ((financePreferences.anyAlerts || alertSettings?.enabled?.value == true) && !Alerts.canNotify(context)) alertsNote = blockedNote
        onPauseOrDispose {}
    }
    // Each notification switch, by a key that survives the phone rotating while the permission is asked for. "alerts"
    // is the server alerts (T68); "review" is the local monthly review, which has no server alert yet (T82).
    val setters = remember(financePreferences, alertSettings) {
        buildMap<String, (Boolean) -> Unit> {
            put("review", financePreferences::setMonthlyReview)
            if (alertSettings != null) put("alerts", alertSettings::setEnabled)
        }
    }
    // The notification switched on while the permission was asked for.
    var waiting by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(key: String, on: Boolean) {
        setters.getValue(key)(on)
        if (key == "alerts" && alertSettings != null) {
            AlertNotifications.reschedule(context, alertSettings)
        } else {
            Alerts.reschedule(context, financePreferences)
        }
    }
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = waiting?.takeIf { it in setters }
        waiting = null
        if (granted) {
            alertsNote = null
            key?.let { apply(it, true) }
        } else {
            alertsNote = blockedNote
        }
    }
    /** Turns a notification on (asking for the permission first, if need be) or off. */
    fun switch(key: String, on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Alerts.canNotify(context)) {
            waiting = key
            askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Before Android 13 nothing is asked: notifications blocked in the phone's settings are said so.
            alertsNote = if (on && !Alerts.canNotify(context)) blockedNote else null
            apply(key, on)
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
                // On with the phone's screen lock since removed: it can still be turned off here.
                enabled = lockAvailable || locking,
            ) { on ->
                val activity = hostActivity as? FragmentActivity
                if (!on) {
                    preferences.setLock(false)
                    lock?.enabledChanged(false)
                } else if (activity != null) {
                    // Confirmed first, so it can't be turned on by someone who couldn't unlock it.
                    authenticate(activity, "Lock Hestia", "Confirm it's you") { ok ->
                        if (ok) {
                            preferences.setLock(true)
                            lock?.enabledChanged(true)
                        }
                    }
                }
            }
            SwitchRow(
                "Hide amounts",
                "Privacy mode: every amount reads \"${Money.HIDDEN}\", here, on the widget and in notifications. " +
                    "Also on the dashboard's eye button.",
                hideAmounts,
            ) { preferences.setHideAmounts(it) }
            SwitchRow(
                "Hide amounts on the widget",
                if (hideAmounts) "Hidden everywhere while privacy mode is on."
                else "The home-screen widget shows no spending or budgets, as anyone can see the home screen.",
                hideWidget || hideAmounts,
                enabled = !hideAmounts,
            ) { financePreferences.setHideWidgetAmounts(it) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Heading("Notifications")
            if (alertSettings != null) {
                SwitchRow(
                    "Needs attention",
                    "A notification when something new needs attention: a budget nearly used up or over its limit, a price change, " +
                        "statements to import, a document that expires, an overdue task, a warranty that ends. The phone asks " +
                        "the server every hour, over Tailscale.",
                    alertsOn,
                ) { switch("alerts", it) }
                SwitchRow("Money", "Budgets nearly used up or over, subscriptions, statements and transactions.", moneyAlerts, enabled = alertsOn, indent = true) {
                    alertSettings.setMoney(it)
                    if (!it) AlertNotifications.removeModule(context, AlertNotificationSettings.MONEY)
                }
                SwitchRow("Documents", "Documents that expire soon, or that expired.", documentAlerts, enabled = alertsOn, indent = true) {
                    alertSettings.setDocuments(it)
                    if (!it) AlertNotifications.removeModule(context, AlertNotificationSettings.DOCUMENTS)
                }
                SwitchRow("Tasks", "Tasks that are overdue.", taskAlerts, enabled = alertsOn, indent = true) {
                    alertSettings.setTasks(it)
                    if (!it) AlertNotifications.removeModule(context, AlertNotificationSettings.TASKS)
                }
                SwitchRow("Home", "Warranties that end soon.", homeAlerts, enabled = alertsOn, indent = true) {
                    alertSettings.setHome(it)
                    if (!it) AlertNotifications.removeModule(context, AlertNotificationSettings.HOME)
                }
                SwitchRow("People", "People to reach out to, birthdays, and lent things not back.", peopleAlerts, enabled = alertsOn, indent = true) {
                    alertSettings.setPeople(it)
                    if (!it) AlertNotifications.removeModule(context, AlertNotificationSettings.PEOPLE)
                }
            }
            SwitchRow(
                "Monthly review",
                "Early each month, the month before: what was spent against the month before it, and where most went.",
                monthlyReview,
            ) { switch("review", it) }
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
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    /** A switch under the one above it (a module of the notifications). */
    indent: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
            .padding(start = if (indent) 40.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
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
