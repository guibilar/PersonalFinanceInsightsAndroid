package com.guibilar.financeinsights.ui.modules

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.guibilar.financeinsights.documents.DocumentsRoute
import com.guibilar.financeinsights.home.HomeRoute
import com.guibilar.financeinsights.people.PeopleRoute
import com.guibilar.financeinsights.tasks.TasksRoute
import com.guibilar.financeinsights.ui.MoneyHomeRoute

/** Whether a module can be opened yet. */
enum class ModuleStatus { Active, ComingSoon }

/**
 * A module of the app: its name and icon on the Modules page, the route of its home ([route], none until it is
 * built), and what it will show on Today ([todayPreview]).
 */
data class HestiaModule(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val route: Any?,
    val status: ModuleStatus,
    val todayPreview: String,
)

/** The modules, in the order the Modules page shows them. The web app lists the same modules (`module-registry.ts`). */
val MODULES = listOf(
    HestiaModule(
        id = "money",
        name = "Money",
        description = "Spending, budgets and net worth",
        icon = Icons.Outlined.AccountBalanceWallet,
        route = MoneyHomeRoute,
        status = ModuleStatus.Active,
        todayPreview = "This month's figures, and what needs attention",
    ),
    HestiaModule(
        id = "documents",
        name = "Documents",
        description = "Papers, receipts and their expiry dates",
        icon = Icons.Outlined.Description,
        route = DocumentsRoute,
        status = ModuleStatus.Active,
        todayPreview = "Documents that expire soon",
    ),
    HestiaModule(
        id = "home",
        name = "Home",
        description = "What you own, and the jobs that keep it working",
        icon = Icons.Outlined.Home,
        route = HomeRoute,
        status = ModuleStatus.Active,
        todayPreview = "Home jobs that are due",
    ),
    HestiaModule(
        id = "people",
        name = "People",
        description = "Birthdays, gifts and lent items",
        icon = Icons.Outlined.Group,
        route = PeopleRoute,
        status = ModuleStatus.Active,
        todayPreview = "Birthdays, and people to get in touch with",
    ),
    HestiaModule(
        id = "journal",
        name = "Journal",
        description = "A few lines a day, and \"On this day\"",
        icon = Icons.Outlined.Book,
        route = null,
        status = ModuleStatus.ComingSoon,
        todayPreview = "On this day, and today's entry",
    ),
    HestiaModule(
        id = "tasks",
        name = "Tasks",
        description = "To-dos and regular maintenance",
        icon = Icons.Outlined.CheckBox,
        route = TasksRoute(),
        status = ModuleStatus.Active,
        todayPreview = "Tasks that are due today",
    ),
)
