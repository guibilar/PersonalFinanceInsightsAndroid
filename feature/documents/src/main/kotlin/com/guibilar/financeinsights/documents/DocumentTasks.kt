package com.guibilar.financeinsights.documents

import com.guibilar.financeinsights.core.tasks.TasksApi

/**
 * What a document page needs from the Tasks module, for its "Tasks" section (T84). The app implements it with
 * `:feature:tasks`, so this module never depends on that one (D14). The navigation host gives it to [documentsGraph];
 * without it, the section does not show.
 */
interface DocumentTasks {
    /** The Tasks API of Hestia.Api, from the signed-in client. */
    val api: TasksApi

    /** False for a read-only user: the section hides "Add task". */
    val canWrite: Boolean get() = true

    /** Opens one task (the Tasks screen with its sheet). */
    fun openTask(id: String)
}
