package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.model.Transaction

/**
 * What the Money pages need from the Tasks module, for "Add task" on a transaction (T86). The app implements it with
 * `:feature:tasks`, so this module never depends on that one (D14).
 */
interface FinanceTasks {
    /** False for a read-only user: the action does not show. */
    val canWrite: Boolean get() = true

    /**
     * Adds a task named after the transaction, linked to it, and opens it to change the details. Null when it was
     * added, or why it failed.
     */
    suspend fun addTask(transaction: Transaction): String?
}
