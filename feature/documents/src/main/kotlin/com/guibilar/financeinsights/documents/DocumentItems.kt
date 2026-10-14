package com.guibilar.financeinsights.documents

import com.guibilar.financeinsights.core.home.HomeApi

/**
 * What a document page needs from the Home module, for its "Items" section (T88): the items that the document belongs
 * to, and "Link to item". The app implements it with `:feature:home`, so this module never depends on that one (D14).
 * The navigation host gives it to [documentsGraph]; without it, the section does not show.
 */
interface DocumentItems {
    /** The Home API of Hestia.Api, from the signed-in client. */
    val api: HomeApi

    /** False for a read-only user: the section hides "Link to item". */
    val canWrite: Boolean get() = true

    /** Opens one item of the home inventory. */
    fun openItem(id: String)
}
