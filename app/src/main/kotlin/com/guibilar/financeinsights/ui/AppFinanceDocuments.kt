package com.guibilar.financeinsights.ui

import androidx.navigation.NavHostController
import com.guibilar.financeinsights.FinanceDocuments
import com.guibilar.financeinsights.core.documents.DocumentsApi
import com.guibilar.financeinsights.documents.DocumentRoute
import com.guibilar.financeinsights.documents.addDocumentTo

/**
 * The Documents pages for a transaction's "Documents" section in Money (T14). `:feature:finance` does not depend on
 * `:feature:documents` (D14): the app joins them here. A document opens over the transactions, so Back returns there.
 */
class AppFinanceDocuments(private val nav: NavHostController, override val api: DocumentsApi) : FinanceDocuments {
    override fun openDocument(id: String) {
        nav.navigate(DocumentRoute(id))
    }

    override fun addDocument(transactionId: String) {
        nav.addDocumentTo(transactionId)
    }
}
