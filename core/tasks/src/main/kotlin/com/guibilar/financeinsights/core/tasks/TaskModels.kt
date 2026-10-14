package com.guibilar.financeinsights.core.tasks

import kotlinx.serialization.Serializable

// The models of the Tasks API of Hestia.Api (module Tasks, T19; contracts in
// Hestia.Modules.Tasks.Contracts/TaskDtos.cs). Ids are GUIDs, as text. Days are "yyyy-MM-dd". Times are ISO 8601.
// An enum value that a newer server sends becomes the field's default (ApiJson coerces it), so it does not fail the page.

/** The state of a task. A repeated task stays [Open]: "done" moves its due date. */
@Serializable
enum class TaskStatus { Open, Done }

/** The unit of a repeat: "every 4 days", "every 12 months". */
@Serializable
enum class RepeatUnit(val one: String, val many: String) {
    Days("day", "days"),
    Weeks("week", "weeks"),
    Months("month", "months"),
    Years("year", "years"),
}

/** How the server finds the next due date of a repeated task. */
@Serializable
enum class RepeatMode {
    /** A fixed schedule from the due date, for example the 1st of each month. */
    FromDueDate,

    /** From the day the task was done, for example "water the plants 4 days after the last time". */
    FromCompletion,
}

/** The repeat of a task. */
@Serializable
data class TaskRepeat(
    /** How many units between two due dates (1 or more). */
    val every: Int = 1,
    val unit: RepeatUnit = RepeatUnit.Days,
    val mode: RepeatMode = RepeatMode.FromDueDate,
)

/** A link from a task to something outside the Tasks module, for example a document. */
@Serializable
data class TaskLink(
    val id: String,
    /** What the link points to, for example [TaskLinkKinds.DOCUMENT]. */
    val kind: String,
    /** The id of the thing, as text. */
    val targetId: String,
    val createdAt: String? = null,
)

/** The kinds of link that the Tasks module knows. */
object TaskLinkKinds {
    /** A document of the Documents module. The target id is the document id. */
    const val DOCUMENT = "Documents.Document"

    /** A finance transaction. The target id is the transaction id. */
    const val TRANSACTION = "Money.Transaction"

    /** An item of the Home module (T22): maintenance. The target id is the item id. */
    const val HOME_ITEM = "Home.Item"
}

/** One time that a task was done. */
@Serializable
data class TaskCompletion(
    val id: String,
    val doneAt: String? = null,
    /** The due date that was done; null for a task without a due date. */
    val dueOn: String? = null,
)

/** A task in a list. */
@Serializable
data class TaskSummary(
    val id: String,
    val title: String = "",
    val notes: String? = null,
    /** The day the task is due, or null for "some day". A repeated task always has one. */
    val dueOn: String? = null,
    /** In lower case. */
    val tags: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.Open,
    /** When a task without a repeat was done. */
    val doneAt: String? = null,
    val repeat: TaskRepeat? = null,
    val sortOrder: Int? = null,
    /** When the task was last done, also for a repeated task. */
    val lastDoneAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Oldest first. */
    val links: List<TaskLink> = emptyList(),
) {
    val isDone get() = status == TaskStatus.Done
}

/** One task, with its last completions. */
@Serializable
data class TaskItem(
    val id: String,
    val title: String = "",
    val notes: String? = null,
    val dueOn: String? = null,
    val tags: List<String> = emptyList(),
    val status: TaskStatus = TaskStatus.Open,
    val doneAt: String? = null,
    val repeat: TaskRepeat? = null,
    val sortOrder: Int? = null,
    val lastDoneAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val links: List<TaskLink> = emptyList(),
    /** The last 10 completions, newest first. */
    val completions: List<TaskCompletion> = emptyList(),
) {
    /** The same task as a list shows it. */
    val summary
        get() = TaskSummary(id, title, notes, dueOn, tags, status, doneAt, repeat, sortOrder, lastDoneAt, createdAt, updatedAt, links)

    /** The details as [TasksApi.update] takes them: all of them, so nothing changes by accident. */
    val details get() = TaskDetails(title, notes, dueOn, tags, repeat, sortOrder)

    val isDone get() = status == TaskStatus.Done
}

/** A page of tasks (`GET /api/tasks`). */
@Serializable
data class TaskPage(
    val items: List<TaskSummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
)

/** A new task (`POST /api/tasks`). Only the title is needed. A repeated task without a due date is due today. */
@Serializable
data class NewTask(
    val title: String,
    val notes: String? = null,
    val dueOn: String? = null,
    val tags: List<String> = emptyList(),
    val repeat: TaskRepeat? = null,
    val sortOrder: Int? = null,
    /** Links to make at once, for example to a document. */
    val links: List<NewTaskLink> = emptyList(),
)

/**
 * The details of a task (`PUT /api/tasks/{id}`). All values replace the old ones: a null value becomes empty. The
 * status, the completions, and the links do not change.
 */
@Serializable
data class TaskDetails(
    val title: String? = null,
    val notes: String? = null,
    val dueOn: String? = null,
    val tags: List<String> = emptyList(),
    val repeat: TaskRepeat? = null,
    val sortOrder: Int? = null,
)

/** A new link (`POST /api/tasks/{id}/links`). */
@Serializable
data class NewTaskLink(val kind: String, val targetId: String) {
    companion object {
        fun document(id: String) = NewTaskLink(TaskLinkKinds.DOCUMENT, id)

        fun transaction(id: String) = NewTaskLink(TaskLinkKinds.TRANSACTION, id)

        fun item(id: String) = NewTaskLink(TaskLinkKinds.HOME_ITEM, id)
    }
}

/** The counts of open tasks (`GET /api/tasks/summary`). */
@Serializable
data class TaskCounts(
    /** Today on the server ("yyyy-MM-dd"), in the time zone of the Tasks module. */
    val date: String? = null,
    val dueToday: Int = 0,
    val overdue: Int = 0,
    /** Due in the next 7 days after today. */
    val upcoming: Int = 0,
)
