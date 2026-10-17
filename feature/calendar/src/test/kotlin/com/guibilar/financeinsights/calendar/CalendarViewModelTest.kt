package com.guibilar.financeinsights.calendar

import com.guibilar.financeinsights.CalendarSamples
import com.guibilar.financeinsights.FakeCalendarApi
import com.guibilar.financeinsights.calendar.ui.CalendarViewModel
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.calendar.CalendarEventRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportItem
import com.guibilar.financeinsights.core.calendar.CalendarImportRequest
import com.guibilar.financeinsights.core.calendar.CalendarImportResult
import com.guibilar.financeinsights.core.logic.DataChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeCalendarApi()
    private val today = CalendarSamples.TODAY

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model(day: String? = null, event: String? = null, agenda: Boolean = false) =
        CalendarViewModel(api, day, event, agenda) { today }

    @Test
    fun `loads the whole weeks of the month`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals("range" to ("2026-09-27" to "2026-10-31"), api.sent.single())
        assertEquals(5, model.state.value.items.size)
        assertEquals(today, model.state.value.selected)
    }

    @Test
    fun `moves a month, and a day of another month moves too`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.moveMonth(1)
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 11), model.state.value.month)
        assertEquals("range" to ("2026-11-01" to "2026-12-05"), api.sent.last())

        model.select(LocalDate.of(2026, 10, 30))
        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 10), model.state.value.month)
        model.goToday()
        advanceUntilIdle()
        assertEquals(today, model.state.value.selected)
    }

    @Test
    fun `the agenda loads 60 days from today`() = runTest(dispatcher) {
        model(agenda = true)
        advanceUntilIdle()

        assertEquals("range" to ("2026-10-16" to "2026-12-14"), api.sent.single())
    }

    @Test
    fun `a link with an event opens it`() = runTest(dispatcher) {
        val model = model(event = "ev1")
        advanceUntilIdle()

        assertEquals("Dentist", model.state.value.target?.event?.title)
    }

    @Test
    fun `adds an event, then loads again and tells the other screens`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        val before = DataChanges.version

        model.newEvent(LocalDate.of(2026, 10, 22))
        assertEquals(LocalDate.of(2026, 10, 22), model.state.value.target?.day)
        model.save(CalendarEventRequest("Swim", allDay = true, startDate = "2026-10-22"))
        advanceUntilIdle()

        assertEquals("Swim", (api.sent.first { it.first == "create" }.second as CalendarEventRequest).title)
        assertNull(model.state.value.target)
        assertTrue(DataChanges.version > before)
        assertEquals("range", api.sent.last().first)
    }

    @Test
    fun `skips the tapped day of a repeated event`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.openEvent("ev3", LocalDate.of(2026, 10, 19))
        advanceUntilIdle()
        model.skip()
        advanceUntilIdle()

        assertEquals("skip" to ("ev3" to "2026-10-19"), api.sent.first { it.first == "skip" })
    }

    @Test
    fun `a refused save stays in the sheet with the reason`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        api.refusing["create"] = ApiError.Http(400, "Bad", null, null, mapOf("title" to listOf("Give the event a title.")))

        model.newEvent()
        model.save(CalendarEventRequest(" ", allDay = true, startDate = "2026-10-22"))
        advanceUntilIdle()

        assertNotNull(model.state.value.target)
        assertEquals("Give the event a title.", model.state.value.sheetError)
    }

    @Test
    fun `a save refused after the sheet closed says why in a message`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        api.refusing["create"] = ApiError.Http(400, "Bad", null, null, mapOf("title" to listOf("Give the event a title.")))

        model.newEvent()
        model.save(CalendarEventRequest(" ", allDay = true, startDate = "2026-10-22"))
        model.closeSheet()
        advanceUntilIdle()

        assertNull(model.state.value.target)
        assertNull(model.state.value.sheetError)
        assertEquals("Give the event a title.", model.messages.first().text)
    }

    @Test
    fun `a save done after another sheet opened leaves that sheet open`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.newEvent()
        model.save(CalendarEventRequest("Swim", allDay = true, startDate = "2026-10-22"))
        model.closeSheet()
        model.newEvent(LocalDate.of(2026, 10, 23))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 10, 23), model.state.value.target?.day)
    }

    @Test
    fun `an ics file is previewed, then imported`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.preview("BEGIN:VCALENDAR")
        advanceUntilIdle()
        assertEquals(1, model.state.value.preview?.result?.added)
        assertEquals(CalendarImportRequest("BEGIN:VCALENDAR", dryRun = true), api.sent.last().second)

        model.confirmImport()
        advanceUntilIdle()
        assertNull(model.state.value.preview)
        assertEquals(CalendarImportRequest("BEGIN:VCALENDAR", dryRun = false), api.sent.first { it.first == "import" && !(it.second as CalendarImportRequest).dryRun }.second)
    }

    @Test
    fun `an import into another month keeps the agenda when loading it again fails`() = runTest(dispatcher) {
        api.importAnswer = CalendarImportResult(
            dryRun = true, added = 1,
            events = listOf(CalendarImportItem("Add", "Trip", allDay = true, startDate = "2026-11-20")),
        )
        val model = model(agenda = true)
        advanceUntilIdle()
        model.preview("BEGIN:VCALENDAR")
        advanceUntilIdle()
        api.failing += "range"

        model.confirmImport()
        advanceUntilIdle()

        assertNotNull(model.state.value.view.valueOrNull)
        assertEquals(LocalDate.of(2026, 11, 20), model.state.value.selected)
    }
}
