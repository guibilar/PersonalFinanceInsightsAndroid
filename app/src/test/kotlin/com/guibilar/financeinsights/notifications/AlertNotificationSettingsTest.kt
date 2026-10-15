package com.guibilar.financeinsights.notifications

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.guibilar.financeinsights.data.FinancePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlertNotificationSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        // The app's start already moved the (empty) settings: start again from the settings of an older app.
        prefs.edit(commit = true) { clear() }
    }

    private fun olderApp(vararg on: String) = prefs.edit(commit = true) { on.forEach { putBoolean(it, true) } }

    @Test
    fun `a new install starts with the notifications off and all modules on`() {
        val settings = AlertNotificationSettings(context)

        assertFalse(settings.enabled.value)
        assertTrue(settings.money.value)
        assertTrue(settings.documents.value)
        assertTrue(settings.tasks.value)
        assertTrue(settings.home.value)
    }

    @Test
    fun `an old price or import switch turns the notifications on, and its setting goes`() {
        olderApp("priceAlerts")
        assertTrue(AlertNotificationSettings(context).enabled.value)

        prefs.edit(commit = true) { clear() }
        olderApp("importReminders")
        assertTrue(AlertNotificationSettings(context).enabled.value)

        assertFalse(prefs.contains("priceAlerts"))
        assertFalse(prefs.contains("importReminders"))
    }

    @Test
    fun `the old budget alerts turn the notifications on, and the local switch goes (T71)`() {
        olderApp("budgetAlerts", "monthlyReview")

        val settings = AlertNotificationSettings(context)
        val finance = FinancePreferences(context)

        assertTrue(settings.enabled.value)
        assertTrue(settings.money.value)
        assertFalse(prefs.contains("budgetAlerts"))
        assertTrue(finance.monthlyReview.value)
    }

    @Test
    fun `after the first migration, budget warnings on turn the server alerts and Money on`() {
        // An app after T68 with its own choices: notifications off, Money off, the local warning on.
        prefs.edit(commit = true) {
            putInt("alertNotifications.version", 1)
            putBoolean("alertNotifications", false)
            putBoolean("alertNotifications.Money", false)
            putBoolean("alertNotifications.Documents", false)
            putBoolean("budgetAlerts", true)
        }

        val settings = AlertNotificationSettings(context)

        assertTrue(settings.enabled.value)
        assertTrue(settings.money.value)
        assertFalse(settings.documents.value)
        assertFalse(prefs.contains("budgetAlerts"))
    }

    @Test
    fun `after the first migration, budget warnings off change nothing`() {
        prefs.edit(commit = true) {
            putInt("alertNotifications.version", 1)
            putBoolean("alertNotifications", false)
            putBoolean("budgetAlerts", false)
        }

        assertFalse(AlertNotificationSettings(context).enabled.value)
        assertFalse(prefs.contains("budgetAlerts"))
    }

    @Test
    fun `only the monthly review on leaves the notifications off`() {
        olderApp("monthlyReview")

        assertFalse(AlertNotificationSettings(context).enabled.value)
    }

    @Test
    fun `the migration runs once`() {
        olderApp("priceAlerts")
        AlertNotificationSettings(context).setEnabled(false)

        // The next start: the choice stays.
        assertFalse(AlertNotificationSettings(context).enabled.value)
    }

    @Test
    fun `each module has its switch, and a newer module follows the main switch only`() {
        val settings = AlertNotificationSettings(context)
        settings.setDocuments(false)

        assertTrue(settings.notifies("Money"))
        assertFalse(settings.notifies("Documents"))
        assertTrue(settings.notifies("Tasks"))
        assertTrue(settings.notifies("Garden"))
        assertEquals(false, AlertNotificationSettings(context).documents.value)
    }

    @Test
    fun `Tasks has its own switch (T84), on after an update from T68`() {
        // The settings of T68: migrated, with no Tasks key.
        prefs.edit(commit = true) {
            putInt("alertNotifications.version", 1)
            putBoolean("alertNotifications", true)
        }
        val settings = AlertNotificationSettings(context)
        assertTrue(settings.tasks.value)

        settings.setTasks(false)

        assertFalse(settings.notifies("Tasks"))
        assertTrue(settings.notifies("Documents"))
        assertEquals(false, AlertNotificationSettings(context).tasks.value)
    }

    @Test
    fun `Home has its own switch (T88), on after an update`() {
        prefs.edit(commit = true) {
            putInt("alertNotifications.version", 1)
            putBoolean("alertNotifications", true)
        }
        val settings = AlertNotificationSettings(context)
        assertTrue(settings.home.value)

        settings.setHome(false)

        assertFalse(settings.notifies("Home"))
        assertTrue(settings.notifies("Tasks"))
        assertEquals(false, AlertNotificationSettings(context).home.value)
    }
}
