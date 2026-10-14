package com.guibilar.financeinsights.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.guibilar.financeinsights.core.api.ApiClient
import com.guibilar.financeinsights.tasks.ui.TaskSheet
import com.guibilar.financeinsights.tasks.ui.TaskViewModel
import com.guibilar.financeinsights.tasks.ui.TasksScreen
import com.guibilar.financeinsights.tasks.ui.TasksViewModel
import kotlinx.serialization.Serializable
import java.time.LocalDate

// The Tasks module's way in: its route, how other pages open it (Today, Capture, Modules, a document), and tasksGraph,
// which the app's navigation host calls. Its APIs come from the signed-in client.

/**
 * The Tasks screen. Web routes `/tasks`, `/tasks?due=overdue` ([overdueOnly]) and `/tasks/<id>` ([task]: its sheet
 * opens at once). [quickAdd] puts the cursor in the quick add (Capture).
 */
@Serializable
data class TasksRoute(val overdueOnly: Boolean = false, val task: String? = null, val quickAdd: Boolean = false)

/** Whether a page is one of the Tasks module's: the app's bar shows them under Modules. */
fun NavDestination.isTasksPage(): Boolean = hasRoute<TasksRoute>()

/**
 * Opens the Tasks screen from outside its pages (Today, Modules, a notification), as a tab opens: over the first
 * page, and Back returns there. [overdueOnly] shows only the overdue tasks.
 */
fun NavHostController.openTasks(overdueOnly: Boolean = false) {
    navigate(TasksRoute(overdueOnly = overdueOnly)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

/** Opens one task (an alert, a document's task): the Tasks screen with its sheet, over the page that is open. */
fun NavHostController.openTask(id: String) {
    navigate(TasksRoute(task = id))
}

/** Opens the quick add over the page that is open (Capture): Back returns there. */
fun NavHostController.addTask() {
    navigate(TasksRoute(quickAdd = true))
}

/**
 * The Tasks pages, for the app's navigation host. [canWrite] false (a read-only user) hides every write action.
 * [openDocument] and [openTransaction] open a linked document or transaction in their modules.
 */
fun NavGraphBuilder.tasksGraph(
    nav: NavHostController,
    client: ApiClient,
    contentPadding: PaddingValues,
    openDocument: (String) -> Unit,
    openTransaction: (String) -> Unit,
    canWrite: Boolean = true,
    today: () -> LocalDate = { LocalDate.now() },
) {
    composable<TasksRoute> { backStack ->
        val route = backStack.toRoute<TasksRoute>()
        val model = viewModel { TasksViewModel(client.api(), route.overdueOnly, today) }
        // The open task, kept when the phone rotates and when a linked document opens and Back returns.
        var open by rememberSaveable { mutableStateOf(route.task) }
        TasksScreen(
            model = model,
            contentPadding = contentPadding,
            openTask = { open = it.id },
            canWrite = canWrite,
            focusQuickAdd = route.quickAdd,
            onBack = { nav.popBackStack() },
            sheet = {
                open?.let { id ->
                    val taskModel = viewModel(key = "task-$id") {
                        TaskViewModel(client.api(), client.api(), client.api(), id, onChanged = model::changed)
                    }
                    LaunchedEffect(taskModel) { taskModel.opened() }
                    TaskSheet(
                        model = taskModel,
                        today = model.state.value.today,
                        canWrite = canWrite,
                        openDocument = openDocument,
                        openTransaction = openTransaction,
                        onDismiss = { open = null },
                    )
                }
            },
        )
    }
}
