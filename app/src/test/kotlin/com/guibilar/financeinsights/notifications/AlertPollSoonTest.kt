package com.guibilar.financeinsights.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T81: the app asks for a poll on opening and on a pull to refresh, at most once every 15 minutes. */
class AlertPollSoonTest {
    private val minute = 60_000L

    @Test
    fun `a poll may run 15 minutes after the last one, not sooner`() {
        assertTrue(AlertNotifications.dueForSoonPoll(last = 0L, now = 1_000_000_000L))
        assertFalse(AlertNotifications.dueForSoonPoll(last = 100 * minute, now = 114 * minute))
        assertTrue(AlertNotifications.dueForSoonPoll(last = 100 * minute, now = 115 * minute))
        // A clock that went back (the user changed it): poll again.
        assertTrue(AlertNotifications.dueForSoonPoll(last = 200 * minute, now = 100 * minute))
    }
}
