package com.guibilar.financeinsights.alerts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.guibilar.financeinsights.data.FinancePreferences
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlertsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = FinancePreferences(context)
    private lateinit var work: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        work = WorkManager.getInstance(context)
    }

    private fun states(name: String) = work.getWorkInfosForUniqueWork(name).get().map { it.state }

    @Test
    fun `on start, moves the check off the old work name and queues it once`() {
        work.enqueueUniquePeriodicWork(
            "budget-alerts",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AlertWorker>(6, TimeUnit.HOURS).build(),
        )
        preferences.setImportReminders(true)

        Alerts.keepScheduled(context, preferences)
        val first = work.getWorkInfosForUniqueWork("alerts").get().single().id
        Alerts.keepScheduled(context, preferences)

        assertEquals(listOf(WorkInfo.State.CANCELLED), states("budget-alerts"))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states("alerts"))
        assertEquals(first, work.getWorkInfosForUniqueWork("alerts").get().single().id)
    }

    @Test
    fun `queues nothing while every alert is off`() {
        Alerts.keepScheduled(context, preferences)
        assertTrue(states("alerts").isEmpty())
    }
}
