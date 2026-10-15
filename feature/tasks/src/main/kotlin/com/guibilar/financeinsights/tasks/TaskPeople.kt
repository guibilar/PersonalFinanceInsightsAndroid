package com.guibilar.financeinsights.tasks

import com.guibilar.financeinsights.core.people.PeopleApi

/**
 * What a task sheet needs from the People module (T93): the people that the task is about (for example "Call Marco"),
 * and "Link to a person". The app implements it with `:feature:people`, so this module never depends on that one (D14).
 * The navigation host gives it to [tasksGraph]; without it, the sheet shows no people.
 */
interface TaskPeople {
    /** The People API of Hestia.Api, from the signed-in client. */
    val api: PeopleApi

    /** Opens one person. */
    fun openPerson(id: String)
}
