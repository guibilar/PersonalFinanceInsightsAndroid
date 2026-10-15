package com.guibilar.financeinsights.ui

import androidx.navigation.NavHostController
import com.guibilar.financeinsights.core.people.PersonLinkKinds
import com.guibilar.financeinsights.documents.DocumentRoute
import com.guibilar.financeinsights.home.ItemRoute
import com.guibilar.financeinsights.people.PeopleHost
import com.guibilar.financeinsights.tasks.openTask

// The app joins the People module (T92) with the other modules here: `:feature:people` and the other feature modules
// do not depend on each other (D14). A linked page opens over the person, so Back returns there.

/** The pages of the things a person links to. */
class AppPeopleHost(private val nav: NavHostController) : PeopleHost {
    override fun openLink(kind: String, targetId: String) {
        when (kind) {
            PersonLinkKinds.DOCUMENT -> nav.navigate(DocumentRoute(targetId))
            PersonLinkKinds.TASK -> nav.openTask(targetId)
            PersonLinkKinds.HOME_ITEM -> nav.navigate(ItemRoute(targetId))
            PersonLinkKinds.TRANSACTION -> nav.openTransaction(targetId)
        }
    }
}
