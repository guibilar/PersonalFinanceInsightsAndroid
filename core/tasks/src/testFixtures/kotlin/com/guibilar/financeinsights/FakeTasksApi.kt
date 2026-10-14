package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.tasks.NewTask
import com.guibilar.financeinsights.core.tasks.NewTaskLink
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskCompletion
import com.guibilar.financeinsights.core.tasks.TaskCounts
import com.guibilar.financeinsights.core.tasks.TaskDetails
import com.guibilar.financeinsights.core.tasks.TaskDue
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskPage
import com.guibilar.financeinsights.core.tasks.TaskRepeat
import com.guibilar.financeinsights.core.tasks.TaskStatus
import com.guibilar.financeinsights.core.tasks.TaskSummary
import com.guibilar.financeinsights.core.tasks.TasksApi
import com.guibilar.financeinsights.core.tasks.UPCOMING_DAYS
import com.guibilar.financeinsights.core.tasks.parseDay
import java.io.IOException
import java.time.LocalDate

/**
 * The Tasks API on a list of tasks in memory, as the server keeps them: in list order (by due date, none last), the
 * filters applied, and done, undo, links and changes kept. "Today" is [today]. Each call is recorded, and any can be
 * made to fail.
 */
class FakeTasksApi(tasks: List<TaskItem> = TasksSamples.all, var today: LocalDate = TasksSamples.TODAY) : TasksApi {
    /** The tasks, in the order they were added. */
    val tasks = tasks.toMutableList()

    /** Calls (by name) that fail as if the server were unreachable. */
    val failing = mutableSetOf<String>()

    /** Calls (by name) answered with this API error instead. */
    val refusing = mutableMapOf<String, ApiError.Http>()

    /** Every call made, by name, with what was sent. */
    val sent = mutableListOf<Pair<String, Any?>>()

    private var nextId = 100

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        refusing[name]?.let { throw it }
        return answer()
    }

    private fun find(id: String) = tasks.firstOrNull { it.id == id } ?: throw ApiError.Http(404, "Not found", "No task $id.", null, emptyMap())

    private fun replace(task: TaskItem): TaskItem {
        tasks.replaceAll { if (it.id == task.id) task else it }
        return task
    }

    private fun now() = "${today}T12:00:00Z"

    /** The list order of the server: by due date, tasks without one last. */
    private fun ordered() = tasks.sortedWith(compareBy<TaskItem> { it.dueOn == null }.thenBy { it.dueOn ?: "" })

    override suspend fun create(task: NewTask): TaskItem = call("create", task) {
        val id = "t${nextId++}"
        val created = TaskItem(
            id = id,
            title = task.title,
            notes = task.notes,
            // A repeated task without a due date is due today.
            dueOn = task.dueOn ?: if (task.repeat != null) today.toString() else null,
            tags = task.tags,
            repeat = task.repeat,
            sortOrder = task.sortOrder,
            createdAt = now(),
            updatedAt = now(),
            links = task.links.mapIndexed { index, link -> TaskLink("k$id-$index", link.kind, link.targetId, now()) },
        )
        tasks += created
        created
    }

    override suspend fun tasks(status: TaskStatus?, tag: String?, due: String?, q: String?, page: Int, pageSize: Int): TaskPage =
        call("tasks", listOf(status, tag, due, q, page, pageSize)) {
            val matching = ordered().filter { task ->
                val day = parseDay(task.dueOn)
                (status == null || task.status == status) &&
                    (tag == null || tag in task.tags) &&
                    (q == null || listOfNotNull(task.title, task.notes, *task.tags.toTypedArray()).any { it.contains(q, ignoreCase = true) }) &&
                    when (due) {
                        null -> true
                        TaskDue.TODAY -> day == today
                        TaskDue.OVERDUE -> task.status == TaskStatus.Open && day != null && day < today
                        TaskDue.UPCOMING -> day != null && day > today && day <= today.plusDays(UPCOMING_DAYS)
                        TaskDue.NONE -> day == null
                        else -> throw ApiError.Http(400, "Bad request", null, null, mapOf("due" to listOf("Use due=today, overdue, upcoming, or none.")))
                    }
            }
            val items = matching.drop((page - 1) * pageSize).take(pageSize).map { it.summary }
            val pages = if (pageSize <= 0) 0 else (matching.size + pageSize - 1) / pageSize
            TaskPage(items, page, pageSize, matching.size, pages, page < pages)
        }

    override suspend fun summary(): TaskCounts = call("summary", null) {
        val open = tasks.filter { it.status == TaskStatus.Open }.mapNotNull { parseDay(it.dueOn) }
        TaskCounts(
            date = today.toString(),
            dueToday = open.count { it == today },
            overdue = open.count { it < today },
            upcoming = open.count { it > today && it <= today.plusDays(UPCOMING_DAYS) },
        )
    }

    override suspend fun byLink(kind: String, id: String): List<TaskSummary> = call("byLink", kind to id) {
        ordered().filter { task -> task.links.any { it.kind == kind && it.targetId == id } }.map { it.summary }
    }

    override suspend fun task(id: String): TaskItem = call("task", id) { find(id) }

    override suspend fun update(id: String, details: TaskDetails): TaskItem = call("update", details) {
        val task = find(id)
        replace(
            task.copy(
                title = details.title.orEmpty(),
                notes = details.notes,
                dueOn = details.dueOn ?: if (details.repeat != null) today.toString() else null,
                tags = details.tags,
                repeat = details.repeat,
                sortOrder = details.sortOrder,
                updatedAt = now(),
            ),
        )
    }

    override suspend fun delete(id: String) = call("delete", id) {
        find(id)
        tasks.removeAll { it.id == id }
        Unit
    }

    override suspend fun done(id: String): TaskItem = call("done", id) {
        val task = find(id)
        if (task.status == TaskStatus.Done) throw ApiError.Http(409, "Conflict", "The task is done already.", null, emptyMap())
        val completion = TaskCompletion("c${nextId++}", now(), task.dueOn)
        val repeat = task.repeat
        val due = parseDay(task.dueOn)
        val done = if (repeat != null && due != null) {
            task.copy(dueOn = next(due, repeat).toString())
        } else {
            task.copy(status = TaskStatus.Done, doneAt = now())
        }
        replace(done.copy(lastDoneAt = now(), updatedAt = now(), completions = listOf(completion) + task.completions))
    }

    override suspend fun undo(id: String): TaskItem = call("undo", id) {
        val task = find(id)
        val last = task.completions.firstOrNull()
            ?: throw ApiError.Http(409, "Conflict", "The task has no \"done\" to undo.", null, emptyMap())
        val reopened = if (task.status == TaskStatus.Done) {
            task.copy(status = TaskStatus.Open, doneAt = null)
        } else {
            task.copy(dueOn = last.dueOn ?: task.dueOn)
        }
        val rest = task.completions.drop(1)
        replace(reopened.copy(completions = rest, lastDoneAt = rest.firstOrNull()?.doneAt, updatedAt = now()))
    }

    override suspend fun addLink(id: String, link: NewTaskLink): TaskLink = call("addLink", link) {
        val task = find(id)
        val created = TaskLink("k${nextId++}", link.kind, link.targetId, now())
        replace(task.copy(links = task.links + created))
        created
    }

    override suspend fun removeLink(id: String, linkId: String) = call("removeLink", linkId) {
        val task = find(id)
        replace(task.copy(links = task.links.filterNot { it.id == linkId }))
        Unit
    }

    /** The next due date, as the server finds it (without its end-of-month rules, which the samples do not need). */
    private fun next(due: LocalDate, repeat: TaskRepeat): LocalDate {
        fun step(day: LocalDate) = when (repeat.unit) {
            RepeatUnit.Days -> day.plusDays(repeat.every.toLong())
            RepeatUnit.Weeks -> day.plusWeeks(repeat.every.toLong())
            RepeatUnit.Months -> day.plusMonths(repeat.every.toLong())
            RepeatUnit.Years -> day.plusYears(repeat.every.toLong())
        }
        if (repeat.mode == RepeatMode.FromCompletion) return step(today)
        var next = step(due)
        while (next <= today) next = step(next)
        return next
    }

    companion object {
        /** A refusal of a write by the server, for a read-only user. */
        val readOnly = ApiError.Http(403, "Forbidden", null, null, emptyMap())
    }
}
