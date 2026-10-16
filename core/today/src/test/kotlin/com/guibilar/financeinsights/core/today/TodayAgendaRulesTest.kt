package com.guibilar.financeinsights.core.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodayAgendaRulesTest {
    private val utc = ZoneOffset.UTC
    private val task = TodayAgendaItem("tasks.task-due.t1", "Tasks", "task-due", "Water the plants", startDate = "2026-10-16", link = "/tasks/t1", done = false)
    private val dinner = TodayAgendaItem(
        "calendar.event.e1", "Calendar", "event", "Dinner with Ana", "Luigi's", allDay = false, startDate = "2026-10-16",
        startsAt = "2026-10-16T18:30:00Z", endsAt = "2026-10-16T20:30:00Z", link = "/calendar?event=e1", eventId = "e1",
    )
    private val birthday = TodayAgendaItem("people.birthday.p1.2026", "People", "birthday", "Ana's birthday", "Turns 36", startDate = "2026-10-21", link = "/people/p1")

    @Test
    fun `sums up the day`() {
        assertEquals("2 things today, and a birthday this week.", daySummary(TodayAgenda(listOf(task, dinner), listOf(birthday))))
        assertEquals("Nothing planned today.", daySummary(TodayAgenda()))
        assertEquals("Here is how things are at home today.", daySummary(null))
    }

    @Test
    fun `the second line and the days to come`() {
        assertEquals("Task", agendaMeta(task, utc))
        assertEquals("Luigi's · until 8:30 PM", agendaMeta(dinner, utc))
        assertEquals("6:30 PM", agendaTime(dinner.startsAt, utc))
        val today = LocalDate.of(2026, 10, 16)
        assertEquals("Wed", upcomingDay(birthday, today, utc))
        assertEquals("Tomorrow 6:30 PM", upcomingDay(dinner.copy(startDate = "2026-10-17"), today, utc))
    }

    @Test
    fun `memories and task ids`() {
        assertEquals("1 year ago · Oct 16, 2025", memoryWhen(TodayMemory("Journal", "journal-entry", 1, "2025-10-16", "x")))
        assertEquals("3 years ago · Oct 16, 2023", memoryWhen(TodayMemory("Journal", "journal-entry", 3, "2023-10-16", "x")))
        assertEquals("t1", taskIdOf(task))
        assertNull(taskIdOf(dinner))
    }
}
