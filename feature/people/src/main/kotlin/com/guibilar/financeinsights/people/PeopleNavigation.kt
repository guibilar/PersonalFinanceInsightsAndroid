package com.guibilar.financeinsights.people

import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.people.ui.PeopleScreen
import com.guibilar.financeinsights.people.ui.PeopleViewModel
import com.guibilar.financeinsights.people.ui.PersonFormScreen
import com.guibilar.financeinsights.people.ui.PersonFormViewModel
import com.guibilar.financeinsights.people.ui.PersonScreen
import com.guibilar.financeinsights.people.ui.PersonViewModel
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The People module's way in: its routes, how other pages open them (Today, Capture, Modules), and peopleGraph, which
// the app's navigation host calls. Its API comes from the signed-in client.

/** The People screen. Web route `/people`. */
@Serializable
data object PeopleRoute

/** One person. Web route `/people/<id>` (the People alerts link here). */
@Serializable
data class PersonRoute(val id: String)

/** The person form: a new person, or (with [id]) the details of one. Web route `/people/new`. */
@Serializable
data class PersonFormRoute(val id: String? = null)

/** Whether a page is one of the People module's: the app's bar shows them under Modules. */
fun NavDestination.isPeoplePage(): Boolean = hasRoute<PeopleRoute>() || hasRoute<PersonRoute>() || hasRoute<PersonFormRoute>()

/** Opens the People screen from outside its pages (Today, Modules), as a tab opens: Back returns to the first page. */
fun NavHostController.openPeople() {
    navigate(PeopleRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** Opens one person from outside (a People alert): over the People screen, so Back shows it. */
fun NavHostController.openPerson(id: String) {
    if (runCatching { getBackStackEntry<PeopleRoute>() }.isFailure) openPeople()
    navigate(PersonRoute(id)) { popUpTo<PeopleRoute>() }
}

/** Opens the form for a new person over the page that is open (Capture): Back returns there. */
fun NavHostController.addPerson() {
    navigate(PersonFormRoute())
}

/**
 * The People pages, for the app's navigation host. [canWrite] false (a read-only user) hides every write action. [host]
 * opens the linked things of the other modules.
 */
fun NavGraphBuilder.peopleGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    host: PeopleHost,
    canWrite: Boolean = true,
    today: () -> LocalDate = { LocalDate.now() },
) {
    composable<PeopleRoute> {
        PeopleScreen(
            model = viewModel { PeopleViewModel(client.api(), today) },
            contentPadding = contentPadding,
            openPerson = { id -> nav.navigate(PersonRoute(id)) },
            addPerson = { nav.navigate(PersonFormRoute()) },
            canWrite = canWrite,
            onBack = { nav.popBackStack() },
        )
    }
    composable<PersonRoute> { backStack ->
        val route = backStack.toRoute<PersonRoute>()
        PersonScreen(
            model = viewModel { PersonViewModel(client.api(), route.id, today) },
            contentPadding = contentPadding,
            openLink = host::openLink,
            edit = { nav.navigate(PersonFormRoute(route.id)) },
            deleted = { nav.popBackStack() },
            onBack = { nav.popBackStack() },
            canWrite = canWrite,
            findLinks = if (host.canFindLinks) host::linkCandidates else null,
        )
    }
    composable<PersonFormRoute> { backStack ->
        val route = backStack.toRoute<PersonFormRoute>()
        PersonFormScreen(
            model = viewModel { PersonFormViewModel(client.api(), route.id, today) },
            contentPadding = contentPadding,
            onClose = { nav.popBackStack() },
            saved = { id ->
                if (route.id == null) {
                    // The new person takes the place of the form, so Back returns to where "Add person" was.
                    nav.navigate(PersonRoute(id)) { popUpTo<PersonFormRoute> { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            },
        )
    }
}
