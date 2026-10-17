package com.guibilar.financeinsights.core.calendar

import com.guibilar.financeinsights.CalendarSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarRulesTest {
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun `a month is whole weeks from Sunday`() {
        val weeks = monthWeeks(YearMonth.of(2026, 10))

        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 27), weeks.first().first())
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last().last())
        assertEquals(6, monthWeeks(YearMonth.of(2026, 8)).size)
        assertEquals("October 2026", monthText(YearMonth.of(2026, 10)))
    }

    @Test
    fun `the items of a day include those of several days`() {
        assertEquals(listOf("Cabin weekend"), itemsOn(CalendarSamples.items, LocalDate.of(2026, 10, 24)).map { it.title })
        assertEquals(listOf("Change the furnace filter", "Dentist"), itemsOn(CalendarSamples.items, LocalDate.of(2026, 10, 20)).map { it.title })
    }

    @Test
    fun `says when an item is`() {
        assertEquals("All day", whenText(CalendarSamples.filter, utc))
        assertEquals("All day · until Sun, Oct 25", whenText(CalendarSamples.cabin, utc))
        assertEquals("3:00 PM – 4:00 PM", whenText(CalendarSamples.dentist, utc))
        assertEquals("9:00 AM", whenText(CalendarSamples.dentist, ZoneId.of("America/Edmonton")).substringBefore(" –"))
    }

    @Test
    fun `names the day`() {
        val today = CalendarSamples.TODAY
        assertEquals("Today", dayHeading(today, today))
        assertEquals("Tomorrow", dayHeading(today.plusDays(1), today))
        assertEquals("Tuesday, October 20", dayHeading(LocalDate.of(2026, 10, 20), today))
        assertEquals("Saturday, January 2, 2027", dayHeading(LocalDate.of(2027, 1, 2), today))
    }

    @Test
    fun `a repeat ends at the end of the phone's day`() {
        val toronto = ZoneId.of("America/Toronto")
        // 11:59:59 PM in Toronto on Dec 31 is 4:59:59 AM UTC on Jan 1: the 8 PM event of Dec 31 is still in the series.
        val rule = joinRepeat("FREQ=WEEKLY", LocalDate.of(2026, 12, 31), allDay = false, toronto)
        assertEquals("FREQ=WEEKLY;UNTIL=20270101T045959Z", rule)
        assertEquals("FREQ=WEEKLY" to LocalDate.of(2026, 12, 31), splitRepeat(rule, toronto))
        assertEquals("Every week until Thu, Dec 31", repeatText(rule, toronto))
        // The end of a UTC day is still that day in Toronto.
        assertEquals("FREQ=WEEKLY" to LocalDate.of(2026, 12, 31), splitRepeat("FREQ=WEEKLY;UNTIL=20261231T235959Z", toronto))
    }

    @Test
    fun `splits and joins a repeat with its end day`() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2" to LocalDate.of(2026, 12, 31), splitRepeat("FREQ=WEEKLY;INTERVAL=2;UNTIL=20261231T235959Z", utc))
        assertEquals("FREQ=WEEKLY;UNTIL=20261231T235959Z", joinRepeat("FREQ=WEEKLY", LocalDate.of(2026, 12, 31), allDay = false, utc))
        assertEquals("FREQ=YEARLY;UNTIL=20300101", joinRepeat("FREQ=YEARLY", LocalDate.of(2030, 1, 1), allDay = true))
        assertEquals("FREQ=YEARLY" to LocalDate.of(2030, 1, 1), splitRepeat("FREQ=YEARLY;UNTIL=20300101"))
        assertNull(joinRepeat("", LocalDate.of(2030, 1, 1), allDay = true))
        assertEquals("Every month", repeatText("FREQ=MONTHLY"))
        assertEquals("Repeats (custom)", repeatText("FREQ=WEEKLY;BYDAY=TU,TH"))
        assertNull(repeatText(null))
    }

    @Test
    fun `a local time is a moment in its zone`() {
        assertEquals("2026-10-20T09:00-06:00", isoMoment(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0), ZoneId.of("America/Edmonton")))
    }

    @Test
    fun `sums up an import`() {
        assertEquals("1 event added.", importSummary(CalendarSamples.importPreview))
        assertEquals("2 events added, 1 changed, 1 skipped.", importSummary(CalendarImportResult(added = 2, updated = 1, skipped = 1)))
        assertEquals("3 already in the calendar.", importSummary(CalendarImportResult(unchanged = 3)))
        assertEquals("The file has no events.", importSummary(CalendarImportResult()))
    }
}
