package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.documents.DocumentsApi

/**
 * What the Money pages need from the Documents module, for the "Documents" section of a transaction (T14). The app
 * implements it with `:feature:documents`, so this module never depends on that one (D14). The navigation host gives it
 * to [com.guibilar.financeinsights.ui.financeGraph]; without it, the section does not show.
 *
 * Not a part of [FinanceHost]: that one is the Application, for work outside the screens, and these calls open pages.
 */
interface FinanceDocuments {
    /** The Documents API of Hestia.Api, from the signed-in client. */
    val api: DocumentsApi

    /** False for a read-only user: the section hides "Add document" and "Link a document". */
    val canWrite: Boolean get() = true

    /** Opens one document. */
    fun openDocument(id: String)

    /**
     * Opens the Documents upload (scan, photos, files). After the upload, the new document is linked to [transactionId]
     * and the page comes back here.
     */
    fun addDocument(transactionId: String)
}
