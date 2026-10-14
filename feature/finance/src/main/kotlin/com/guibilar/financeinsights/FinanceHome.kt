package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.model.Transaction

/**
 * What the Money pages need from the Home module, for "Add to inventory" on a transaction (T88). The app implements it
 * with `:feature:home`, so this module never depends on that one (D14). The navigation host gives it to
 * [com.guibilar.financeinsights.ui.financeGraph]; without it, the action does not show.
 */
interface FinanceHome {
    /** False for a read-only user: the action does not show. */
    val canWrite: Boolean get() = true

    /** Opens a new item of the home inventory with the transaction's description, amount and day, linked to it. */
    fun addToInventory(transaction: Transaction)
}
