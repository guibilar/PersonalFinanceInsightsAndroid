package com.guibilar.financeinsights.people

/**
 * What the People pages need from the app and the other modules (D14): to open a linked thing in its module, and to
 * find things to link (T94). The app implements it; this module never depends on another feature module.
 */
interface PeopleHost {
    /** Opens a linked thing: a document, a task, a home item, or a transaction (a [PersonLinkKinds] kind and its id). */
    fun openLink(kind: String, targetId: String)

    /** True when [linkCandidates] can search: the person page then offers "Add a link". */
    val canFindLinks: Boolean get() = false

    /**
     * "Add a link" (T94): the things of [kind] (a [PersonLinkKinds] kind) that match [text], or the latest ones without
     * it. A failure throws; the page says so.
     */
    suspend fun linkCandidates(kind: String, text: String): List<LinkCandidate> = emptyList()
}

/** A thing to link to a person, as the "Add a link" sheet lists it. */
data class LinkCandidate(val id: String, val title: String, val subtitle: String = "")
