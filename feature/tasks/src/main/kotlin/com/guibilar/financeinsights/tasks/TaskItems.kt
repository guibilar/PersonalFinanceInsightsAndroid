package com.guibilar.financeinsights.tasks

import com.guibilar.financeinsights.core.home.HomeApi

/**
 * What a task sheet needs from the Home module (T88): the items that the task belongs to (maintenance), and "Link to
 * an item". The app implements it with `:feature:home`, so this module never depends on that one (D14). The navigation
 * host gives it to [tasksGraph]; without it, the sheet shows no items.
 */
interface TaskItems {
    /** The Home API of Hestia.Api, from the signed-in client. */
    val api: HomeApi

    /** Opens one item of the home inventory. */
    fun openItem(id: String)
}
