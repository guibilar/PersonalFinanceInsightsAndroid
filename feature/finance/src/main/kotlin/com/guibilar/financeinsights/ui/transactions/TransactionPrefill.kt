package com.guibilar.financeinsights.ui.transactions

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * The values of the next "Add a transaction" sheet, from a receipt (T100): the store, the total, and the day. After the
 * add, [onAdded] gets the id of the new transaction (the receipt links to it).
 */
data class TransactionPrefill(
    val description: String,
    /** Money out, as a positive amount. */
    val amount: Double?,
    val date: LocalDate?,
    val onAdded: (suspend (transactionId: String) -> Unit)? = null,
)

/**
 * The prefill that waits for the add sheet. Set it, then ask for the sheet (QuickAction.AddTransaction): the sheet takes
 * it once and clears it.
 */
object TransactionPrefills {
    val pending = MutableStateFlow<TransactionPrefill?>(null)
}
