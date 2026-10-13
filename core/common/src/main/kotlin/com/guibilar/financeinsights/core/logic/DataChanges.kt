package com.guibilar.financeinsights.core.logic

import java.util.concurrent.atomic.AtomicLong

/**
 * Says that the data changed from this app (a statement imported, a transaction added), so a screen that shows a
 * summary of it, such as Today, asks the server for a new answer and not its short cache. The same idea as the web's
 * `DataChangesService`.
 */
object DataChanges {
    private val counter = AtomicLong()

    /** Goes up on each change. Keep the value of the last load, and compare it later. */
    val version: Long get() = counter.get()

    fun changed() {
        counter.incrementAndGet()
    }
}
