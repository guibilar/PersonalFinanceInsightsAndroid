package com.guibilar.financeinsights.core.logic

import com.guibilar.financeinsights.core.api.FinanceApi
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.model.Transaction
import com.guibilar.financeinsights.core.model.TransactionCriteria
import com.guibilar.financeinsights.core.model.TransactionFilter

// One transaction by its id (Hestia D13): a link to it (Today, a document, the "View" after an add) finds it here.

/** What a look-up of one transaction found. */
sealed interface TransactionLookup {
    data class Found(val transaction: Transaction) : TransactionLookup

    /** It is deleted (hidden): the deleted list has it, to restore it. */
    data class Deleted(val transaction: Transaction) : TransactionLookup

    /** No transaction has this id, or the id is not a GUID. */
    data object Missing : TransactionLookup

    /** The server did not answer, or refused the look-up. */
    data object Failed : TransactionLookup
}

private val GUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** True when [id] can be a transaction id. The API refuses other ids with 400, so the app does not send them. */
fun isTransactionId(id: String): Boolean = GUID.matches(id)

/**
 * Finds one transaction: first in the live ones, then in the deleted ones. An id that is not a GUID is
 * [TransactionLookup.Missing], with no API call.
 */
suspend fun FinanceApi.findTransaction(id: String): TransactionLookup {
    if (!isTransactionId(id)) return TransactionLookup.Missing
    val live = apiCall { transactions(TransactionFilter(transactionId = id, page = 1, pageSize = 1)) }
    live.getOrNull()?.items?.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return TransactionLookup.Found(it) }
    if (live.isFailure) return TransactionLookup.Failed
    val hidden = apiCall { transactions(TransactionFilter(transactionId = id, hidden = true, page = 1, pageSize = 1)) }
    hidden.getOrNull()?.items?.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return TransactionLookup.Deleted(it) }
    return if (hidden.isFailure) TransactionLookup.Failed else TransactionLookup.Missing
}

/** The filters that show [transaction] in the list: its day and its account, and nothing more. */
fun linkCriteria(transaction: Transaction): TransactionCriteria {
    val day = transaction.day.toString()
    return TransactionCriteria(accountId = transaction.accountId, startDate = day, endDate = day)
}
