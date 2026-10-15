package com.guibilar.financeinsights.journal

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
import com.guibilar.financeinsights.journal.ui.JournalScreen
import com.guibilar.financeinsights.journal.ui.JournalViewModel
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The Journal module's way in (T21): its route, how other pages open it (Today, Capture, Modules), and journalGraph,
// which the app's navigation host calls. Its API comes from the signed-in client.

/** The Journal screen, on [day] ("yyyy-MM-dd"; null: today). Web route `/journal?day=…`. */
@Serializable
data class JournalRoute(val day: String? = null)

/** Whether a page is the Journal's: the app's bar shows it under Modules. */
fun NavDestination.isJournalPage(): Boolean = hasRoute<JournalRoute>()

/** Opens the Journal from outside its page (Today, Modules, Capture), as a tab opens: Back returns to the first page. */
fun NavHostController.openJournal(day: String? = null) {
    navigate(JournalRoute(day)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** The Journal page, for the app's navigation host. [canWrite] false (a read-only user) makes the editor read only. */
fun NavGraphBuilder.journalGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    canWrite: Boolean = true,
    today: () -> LocalDate = { LocalDate.now() },
) {
    composable<JournalRoute> { backStack ->
        val route = backStack.toRoute<JournalRoute>()
        JournalScreen(
            model = viewModel { JournalViewModel(client.api(), route.day, today) },
            contentPadding = contentPadding,
            canWrite = canWrite,
            onBack = { nav.popBackStack() },
        )
    }
}
