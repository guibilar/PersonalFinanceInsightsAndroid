package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.model.BudgetInput
import com.guibilar.financeinsights.core.model.BudgetPeriod
import com.guibilar.financeinsights.core.model.Status
import com.guibilar.financeinsights.ui.budgets.BudgetsViewModel
import com.guibilar.financeinsights.ui.budgets.budgetErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the form says what is missing, as on the web`() {
        val errors = budgetErrors("", " ", "12.505", BudgetPeriod.Custom, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), "")
        assertEquals(
            mapOf(
                "name" to "Enter a name.",
                "description" to "Enter a description.",
                "amount" to "Use whole cents: at most 2 decimal places.",
                "days" to "The last day can't be before the first.",
            ),
            errors,
        )
        assertTrue(budgetErrors("Food", "d", "400", BudgetPeriod.Monthly, null, null, "no frills").isEmpty())
    }

    @Test
    fun `a failed later page keeps the list and offers to try again`() = runTest(dispatcher) {
        api.budgetCount = 6
        val model = BudgetsViewModel(api)
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)

        api.failing += "budgets"
        model.loadMore()
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)
        assertTrue(model.state.value.moreFailed)

        api.failing.clear()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(4, model.state.value.items.size)
        assertFalse(model.state.value.moreFailed)
    }

    @Test
    fun `searches by name after a pause, and filters by status`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()

        model.search("gro")
        model.search("groceries")
        advanceTimeBy(BudgetsViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        model.setStatus(Status.Inactive)
        advanceUntilIdle()

        assertEquals("groceries", model.state.value.name)
        assertEquals(Status.Inactive, model.state.value.status)
        assertEquals(2, model.state.value.items.size)
    }

    @Test
    fun `the same search typed again just after clearing searches again`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()
        model.search("groceries")
        advanceTimeBy(BudgetsViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        model.clearAll()
        model.search("groceries")
        advanceTimeBy(BudgetsViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals("groceries", model.state.value.name)
    }

    @Test
    fun `a category another budget counts is explained, and nothing is added`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()
        api.refusing["addBudget"] = FakeFinanceApi.conflict("Groceries is already counted by the budget \"Groceries\".")
        val input = BudgetInput("Food", "d", Status.Active, 400.0, BudgetPeriod.Monthly, categoryIds = listOf("c1"))

        val error = model.save(null, input)
        api.refusing.clear()
        val saved = model.save(model.state.value.items.first(), input)

        assertEquals("Groceries is already counted by the budget \"Groceries\".", error)
        assertNull(saved)
        assertEquals("updateBudget", api.sent.last().first)
    }

    @Test
    fun `closing the form while it saves still adds the budget, and says so`() = runTest(dispatcher) {
        val model = BudgetsViewModel(api)
        advanceUntilIdle()
        api.slow["addBudget"] = 100
        val input = BudgetInput("Trip", "d", Status.Active, 900.0, BudgetPeriod.Total)

        val form = launch { model.save(null, input) }
        advanceTimeBy(50)
        form.cancel()
        advanceUntilIdle()

        assertEquals("addBudget" to input, api.sent.last())
        assertEquals("Added “Trip”", model.messages.first().text)
    }
}
