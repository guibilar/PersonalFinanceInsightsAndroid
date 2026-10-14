package com.guibilar.financeinsights

import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskCompletion
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskRepeat
import com.guibilar.financeinsights.core.tasks.TaskStatus
import java.time.LocalDate

/**
 * Tasks as the server gives them, on Saturday, October 3, 2026 ([TODAY], the day of the other samples). One task links
 * to the car insurance document ("d1" of `DocumentsSamples`), one to the METRO 221 transaction of the recorded finance
 * responses.
 */
object TasksSamples {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 3)

    /** The car insurance policy of the document samples. */
    const val INSURANCE_DOCUMENT = "d1"

    /** The METRO 221 transaction of the recorded finance responses (Oct 3, 2026, $41.92). */
    const val METRO_TRANSACTION = "01a0ff22-674a-7e98-bb70-9dab0d96bb50"

    private const val CREATED = "2026-09-20T10:00:00Z"

    val overdue = TaskItem(
        id = "t1",
        title = "Call the landlord about the lease",
        dueOn = "2026-10-01",
        tags = listOf("home"),
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    val statement = TaskItem(
        id = "t2",
        title = "Import the Neo card statement",
        dueOn = "2026-10-03",
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(TaskLink("k2", TaskLinkKinds.TRANSACTION, METRO_TRANSACTION, CREATED)),
    )

    val insurance = TaskItem(
        id = "t3",
        title = "Compare car insurance quotes",
        notes = "The policy ends on Oct 25. Ask two brokers.",
        dueOn = "2026-10-03",
        tags = listOf("car"),
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(TaskLink("k3", TaskLinkKinds.DOCUMENT, INSURANCE_DOCUMENT, CREATED)),
    )

    val paper = TaskItem(id = "t4", title = "Buy printer paper", dueOn = "2026-10-03", createdAt = CREATED, updatedAt = CREATED)

    val plants = TaskItem(
        id = "t5",
        title = "Water the plants",
        dueOn = "2026-10-06",
        repeat = TaskRepeat(4, RepeatUnit.Days, RepeatMode.FromCompletion),
        lastDoneAt = "2026-10-02T08:00:00Z",
        createdAt = CREATED,
        updatedAt = "2026-10-02T08:00:00Z",
        completions = listOf(
            TaskCompletion("c52", "2026-10-02T08:00:00Z", "2026-10-01"),
            TaskCompletion("c51", "2026-09-27T09:30:00Z", "2026-09-27"),
        ),
    )

    val backup = TaskItem(
        id = "t6",
        title = "Server backup check",
        dueOn = "2026-10-04",
        tags = listOf("server"),
        repeat = TaskRepeat(1, RepeatUnit.Weeks, RepeatMode.FromDueDate),
        lastDoneAt = "2026-09-27T20:00:00Z",
        createdAt = CREATED,
        updatedAt = "2026-09-27T20:00:00Z",
        completions = listOf(TaskCompletion("c61", "2026-09-27T20:00:00Z", "2026-09-27")),
    )

    val dentist = TaskItem(id = "t7", title = "Book a dentist visit", dueOn = "2026-10-08", createdAt = CREATED, updatedAt = CREATED)

    val garage = TaskItem(id = "t8", title = "Sort the garage", tags = listOf("home"), createdAt = CREATED, updatedAt = CREATED)

    val handouts = TaskItem(
        id = "t9",
        title = "Print session handouts",
        dueOn = "2026-10-03",
        status = TaskStatus.Done,
        doneAt = "2026-10-03T09:00:00Z",
        lastDoneAt = "2026-10-03T09:00:00Z",
        createdAt = CREATED,
        updatedAt = "2026-10-03T09:00:00Z",
        completions = listOf(TaskCompletion("c91", "2026-10-03T09:00:00Z", "2026-10-03")),
    )

    /** In the server's list order: by due date (none last). */
    val all = listOf(overdue, statement, insurance, paper, backup, plants, dentist, handouts, garage)
}
