package com.guibilar.financeinsights.security

import androidx.test.core.app.ApplicationProvider
import com.guibilar.financeinsights.data.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLockTest {
    private val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
    private var now = 0L

    @Test
    fun `locks on launch and after a minute away, not after a short trip out`() {
        preferences.setLock(true)
        val lock = AppLock(preferences) { now }
        assertTrue(lock.locked.value)
        lock.unlocked()

        lock.onBackground(); now += 20_000; lock.onForeground()
        assertFalse(lock.locked.value)

        lock.onBackground(); now += AppLock.GRACE_MS; lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `off, it never locks, and turning it off unlocks`() {
        preferences.setLock(false)
        val lock = AppLock(preferences) { now }
        assertFalse(lock.locked.value)
        lock.onBackground(); now += 10 * AppLock.GRACE_MS; lock.onForeground()
        assertFalse(lock.locked.value)

        preferences.setLock(true)
        val locked = AppLock(preferences) { now }
        preferences.setLock(false)
        locked.enabledChanged(false)
        assertFalse(locked.locked.value)
    }
}
