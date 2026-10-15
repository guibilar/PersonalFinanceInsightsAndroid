package com.guibilar.financeinsights.inbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.guibilar.financeinsights.core.api.ApiClient
import kotlinx.serialization.Serializable

// The Inbox module's way in (T18): its route, how other pages open it (Today, Capture, Modules), and inboxGraph.

/** The Inbox. Web route `/inbox`. */
@Serializable
data object InboxRoute

/** Whether a page is the Inbox: the app's bar shows it under Modules. */
fun NavDestination.isInboxPage(): Boolean = hasRoute<InboxRoute>()

/** Opens the Inbox from outside (Today, Modules, Capture), as a tab opens: Back returns to the first page. */
fun NavHostController.openInbox() {
    navigate(InboxRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** The Inbox page, for the app's navigation host. */
fun NavGraphBuilder.inboxGraph(nav: NavHostController, client: ApiClient, contentPadding: PaddingValues, canWrite: Boolean = true) {
    composable<InboxRoute> {
        InboxScreen(
            model = viewModel { InboxViewModel(client.api(), NoteSender(client.api(), client.api())) },
            contentPadding = contentPadding,
            canWrite = canWrite,
            onBack = { nav.popBackStack() },
        )
    }
}
