package com.guibilar.financeinsights.people

/**
 * What the People pages need from the app and the other modules (D14): to open a linked thing in its module. The app
 * implements it; this module never depends on another feature module.
 */
interface PeopleHost {
    /** Opens a linked thing: a document, a task, a home item, or a transaction (a [PersonLinkKinds] kind and its id). */
    fun openLink(kind: String, targetId: String)
}
