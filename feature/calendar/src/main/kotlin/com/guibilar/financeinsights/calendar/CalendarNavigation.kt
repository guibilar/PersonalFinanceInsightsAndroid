package com.guibilar.financeinsights.calendar

import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.guibilar.financeinsights.calendar.ui.CalendarScreen
import com.guibilar.financeinsights.calendar.ui.CalendarViewModel
import com.guibilar.financeinsights.core.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The Calendar module's way in (T104): its route, how other pages open it (Today, Modules, a shared .ics file), and
// calendarGraph, which the app's navigation host calls. Its API comes from the signed-in client.

/**
 * The Calendar screen, on [day] ("yyyy-MM-dd"; null: today), with [event] open (an event id), or in the agenda view.
 * Web route `/calendar?day=…&event=…&view=agenda`.
 */
@Serializable
data class CalendarRoute(val day: String? = null, val event: String? = null, val agenda: Boolean = false)

/** Whether a page is the Calendar's: the app's bar shows it under Modules. */
fun NavDestination.isCalendarPage(): Boolean = hasRoute<CalendarRoute>()

/** Opens the Calendar from outside its page (Today, Modules, a share), as a tab opens: Back returns to the first page. */
fun NavHostController.openCalendar(day: String? = null, event: String? = null) {
    navigate(CalendarRoute(day, event)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/**
 * The Calendar page, for the app's navigation host. [canWrite] false (a read-only user) shows no add, change, or import.
 * [onOpenLink] opens the item of another module by its web link (the app's link map). [sharedIcs] holds the text of an
 * .ics file shared to the app (T101): the screen shows its import preview, then clears it.
 */
fun NavGraphBuilder.calendarGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    canWrite: Boolean = true,
    onOpenLink: (String) -> Unit = {},
    sharedIcs: MutableStateFlow<String?> = MutableStateFlow(null),
    today: () -> LocalDate = { LocalDate.now() },
) {
    composable<CalendarRoute> { backStack ->
        val route = backStack.toRoute<CalendarRoute>()
        CalendarScreen(
            model = viewModel { CalendarViewModel(client.api(), route.day, route.event, route.agenda, today) },
            contentPadding = contentPadding,
            canWrite = canWrite,
            onOpenLink = onOpenLink,
            sharedIcs = sharedIcs,
            onBack = { nav.popBackStack() },
        )
    }
}
