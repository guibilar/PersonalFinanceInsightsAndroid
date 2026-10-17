package com.guibilar.financeinsights.timeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeTimelineApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a chip picked after Show earlier during a quiet reload shows only its module`() = runTest(dispatcher) {
        // The first page of all modules answers late once the screen is shown; the others at once.
        var slow = false
        val timeline = object : TimelineApi {
            override suspend fun page(before: String?, days: Int, module: String?): TimelinePage {
                if (slow && before == null && module == null) delay(1_000)
                return api.page(before, days, module)
            }
        }
        val model = TimelineViewModel(timeline)
        advanceUntilIdle()

        slow = true
        model.resumed()
        model.resumed()
        runCurrent()
        model.showEarlier()
        model.pick("Money")
        advanceUntilIdle()

        assertEquals("Money", model.state.value.module)
        assertEquals(setOf("Money"), model.state.value.days.flatMap { it.entries }.map { it.module }.toSet())
    }

    @Test
    fun `a quiet reload keeps the pages of Show earlier`() = runTest(dispatcher) {
        val model = TimelineViewModel(api)
        advanceUntilIdle()
        model.showEarlier()
        advanceUntilIdle()
        assertEquals(2, model.state.value.pages.size)

        model.resumed()
        model.resumed()
        advanceUntilIdle()

        assertEquals(2, model.state.value.pages.size)
        assertEquals("2026-09-25", model.state.value.days.last().day)
    }
}
