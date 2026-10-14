package com.guibilar.financeinsights.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.guibilar.financeinsights.AlertSamples
import com.guibilar.financeinsights.FakeAlertsApi
import com.guibilar.financeinsights.MainActivity
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.today.AlertDto
import com.guibilar.financeinsights.core.today.AlertsDto
import com.guibilar.financeinsights.core.today.SeenAlerts
import com.guibilar.financeinsights.core.today.TodayValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlertPollerTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val owner = "https://hestia.example.ts.net|dev"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: SeenAlertStore
    private lateinit var settings: AlertNotificationSettings
    private val api = FakeAlertsApi()

    private fun alert(alertId: String, module: String = "Money", link: String? = "/money/budgets") = AlertDto(
        id = "$module.$alertId", alertId = alertId, module = module, kind = "budget-over", title = "Budget over its limit",
        text = "{budget} is over by {over}", values = mapOf("budget" to TodayValue.plain("Groceries"), "over" to TodayValue.money(12.5)),
        link = link,
    )

    private fun answer(vararg alerts: AlertDto) {
        api.answer = AlertsDto(alerts = alerts.toList())
    }

    private fun poller() = AlertPoller(context, settings, store, canNotify = { true })

    private fun poll(first: Boolean = false) = runBlocking { poller().poll(api, owner, first) }

    private fun seen() = runBlocking { store.read() }

    /** The alert ids of the notifications on screen, and the summary's text. */
    private fun shownIds() = AlertNotifications.shown(context).first.map { it.alertId }.toSet()

    private fun notification(alertId: String): Notification =
        manager.activeNotifications.single { it.tag?.endsWith("|$alertId") == true }.notification

    @Before
    fun setUp() {
        store = SeenAlertStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "seen.preferences_pb") })
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        settings = AlertNotificationSettings(context).apply { setEnabled(true) }
        AlertNotifications.createChannel(context)
    }

    @After
    fun tearDown() {
        Money.hidden = { false }
        scope.cancel()
    }

    @Test
    fun `the first poll keeps the open alerts and notifies nothing`() {
        answer(alert("a"), alert("b"))

        poll()

        assertTrue(shownIds().isEmpty())
        assertEquals(SeenAlerts(owner, setOf("a", "b")), seen())
    }

    @Test
    fun `a new alert gets a notification with its filled text, and a seen one does not`() {
        answer(alert("a"))
        poll()
        answer(alert("a"), alert("b"))

        poll()

        assertEquals(setOf("b"), shownIds())
        val shown = notification("b")
        assertEquals("Budget over its limit", shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Groceries is over by $12.50", shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(setOf("a", "b"), seen()!!.alertIds)

        // The same answer again: nothing new.
        manager.cancelAll()
        poll()
        assertTrue(shownIds().isEmpty())
    }

    @Test
    fun `hide amounts hides the amounts in the notification too`() {
        answer()
        poll()
        Money.hidden = { true }
        answer(alert("a"))

        poll()

        assertEquals("Groceries is over by ${Money.HIDDEN}", notification("a").extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `a tap opens the alert's link in the app`() {
        answer()
        poll()
        answer(alert("a", link = "/money/transactions?importAccount=acc1#import"))

        poll()

        val tap = shadowOf(notification("a").contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertEquals(AlertNotifications.ACTION_OPEN_ALERT, tap.action)
        assertEquals("/money/transactions?importAccount=acc1#import", tap.getStringExtra(AlertNotifications.EXTRA_LINK))
    }

    @Test
    fun `a resolved alert removes its notification`() {
        answer()
        poll()
        answer(alert("a"), alert("b"))
        poll()

        answer(alert("b"))
        poll()

        assertEquals(setOf("b"), shownIds())
        assertEquals(setOf("b"), seen()!!.alertIds)
    }

    @Test
    fun `a failed poll changes nothing`() {
        answer()
        poll()
        answer(alert("a"))
        poll()
        api.failing = true

        val result = poll()

        assertTrue(result is AlertPollResult.Failed)
        assertEquals(setOf("a"), shownIds())
        assertEquals(setOf("a"), seen()!!.alertIds)
    }

    @Test
    fun `signed out, turned off, or not allowed: the poll does nothing`() {
        answer(alert("a"))

        assertEquals(AlertPollResult.Skipped, runBlocking { poller().poll(null, owner) })
        settings.setEnabled(false)
        assertEquals(AlertPollResult.Skipped, poll())
        settings.setEnabled(true)
        assertEquals(AlertPollResult.Skipped, runBlocking { AlertPoller(context, settings, store, canNotify = { false }).poll(api, owner) })

        assertTrue(api.calls.isEmpty())
        assertNull(seen())
    }

    @Test
    fun `a module that is off gets no notification, and its shown ones go`() {
        answer()
        poll()
        answer(alert("m"), alert("d", module = "Documents", link = "/documents/d1"))
        poll()
        assertEquals(setOf("m", "d"), shownIds())

        settings.setDocuments(false)
        answer(alert("m"), alert("d", module = "Documents"), alert("d2", module = "Documents"))
        poll()

        assertEquals(setOf("m"), shownIds())
        assertEquals(setOf("m", "d", "d2"), seen()!!.alertIds)
    }

    @Test
    fun `more than three new alerts give one summary, which goes when they are resolved`() {
        answer()
        poll()
        answer(alert("a"), alert("b"), alert("c"), AlertSamples.documentExpiring)

        poll()

        assertTrue(shownIds().isEmpty())
        val summary = manager.activeNotifications.single()
        assertEquals("4 new items need attention", summary.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())

        answer(alert("x"))
        poll()
        assertTrue(manager.activeNotifications.none { it.tag == summary.tag })
    }

    @Test
    fun `another account starts over`() {
        answer(alert("a"))
        poll()
        answer(alert("a"), alert("b"))

        runBlocking { poller().poll(api, "https://hestia.example.ts.net|other") }

        assertTrue(shownIds().isEmpty())
        assertEquals("https://hestia.example.ts.net|other", seen()!!.owner)
    }

    @Test
    fun `a first poll on request notifies nothing, also with stored ids`() {
        answer()
        poll()
        answer(alert("a"))

        poll(first = true)

        assertTrue(shownIds().isEmpty())
        assertEquals(setOf("a"), seen()!!.alertIds)
    }

    @Test
    fun `turning on polls now as a first poll and every hour, and turning off stops and removes`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val work = WorkManager.getInstance(context)

        AlertNotifications.reschedule(context, settings)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), work.getWorkInfosForUniqueWork("alert-notifications").get().map { it.state })
        assertTrue(work.getWorkInfosForUniqueWork("alert-notifications-now").get().isNotEmpty())

        answer()
        poll()
        answer(alert("a"))
        poll()
        settings.setEnabled(false)
        AlertNotifications.reschedule(context, settings)

        assertEquals(listOf(WorkInfo.State.CANCELLED), work.getWorkInfosForUniqueWork("alert-notifications").get().map { it.state })
        assertTrue(shownIds().isEmpty())
    }
}
