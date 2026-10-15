package com.guibilar.financeinsights.documents

import com.guibilar.financeinsights.core.people.PeopleApi

/**
 * What a document page needs from the People module, for its "People" section (T93): the people that the document is
 * about, and "Link a person". The app implements it with `:feature:people`, so this module never depends on that one
 * (D14). The navigation host gives it to [documentsGraph]; without it, the section does not show.
 */
interface DocumentPeople {
    /** The People API of Hestia.Api, from the signed-in client. */
    val api: PeopleApi

    /** False for a read-only user: the section hides "Link a person". */
    val canWrite: Boolean get() = true

    /** Opens one person. */
    fun openPerson(id: String)
}
