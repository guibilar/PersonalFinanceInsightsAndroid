package com.guibilar.financeinsights.ui

import com.guibilar.financeinsights.FakeFinanceApi
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.ImportResult
import com.guibilar.financeinsights.core.model.TransactionFilter
import com.guibilar.financeinsights.core.model.TransactionInput
import com.guibilar.financeinsights.core.model.TransactionType
import com.guibilar.financeinsights.core.model.TransactionUpdate
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.transactions.AddOutcome
import com.guibilar.financeinsights.ui.transactions.ImportOutcome
import com.guibilar.financeinsights.ui.transactions.PickedFile
import com.guibilar.financeinsights.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionActionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeFinanceApi()
    private val today = LocalDate.of(2026, 10, 3)
    private val input = TransactionInput("2026-10-02", "COFFEE", 4.5, TransactionType.Debit, "a1")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.model(): Pair<TransactionsViewModel, MutableList<UiMessage>> {
        val model = TransactionsViewModel(api, TransactionsRoute()) { today }
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.messages.collect { messages += it } }
        return model to messages
    }

    private fun reloads() = api.transactionFilters.size

    @Test
    fun `adding remembers the day and account for the next one, and reloads`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        val before = reloads()

        val outcome = model.add(input)
        advanceUntilIdle()

        assertEquals(AddOutcome.Added, outcome)
        assertEquals(LocalDate.of(2026, 10, 2) to "a1", model.state.value.lastAdded)
        assertEquals(before + 1, reloads())
        assertTrue(messages.single().text.startsWith("Added “COFFEE”"))
    }

    @Test
    fun `an identical transaction is offered to add anyway, other refusals say why`() = runTest(dispatcher) {
        val (model, _) = model()
        api.refusing["addTransaction"] = FakeFinanceApi.conflict("An identical transaction already exists.")
        assertEquals(AddOutcome.Duplicate("An identical transaction already exists."), model.add(input))

        api.refusing["addTransaction"] = ApiError.Http(400, "Invalid", null, null, mapOf("amount" to listOf("Use whole cents.")))
        assertEquals(AddOutcome.Failed("Use whole cents."), model.add(input))
    }

    @Test
    fun `an edit with a rule adds it first, then applies the rules`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        val transaction = model.state.value.list.items.first()

        val error = model.saveEdit(transaction, TransactionUpdate("Coffee", "c2", Flow.Ignored), CategoryRuleInput("STARBUCKS", "c2", 0))
        advanceUntilIdle()

        assertNull(error)
        assertEquals(listOf("updateTransaction", "addCategoryRule", "applyRules"), api.sent.map { it.first })
        assertEquals("Transaction updated. Rule “STARBUCKS” added and 3 transactions updated by the rules", messages.single().text)
    }

    @Test
    fun `a rule refused after the edit still saves the edit, and says so`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        api.refusing["addCategoryRule"] = FakeFinanceApi.conflict("A rule for \"STARBUCKS\" already exists.")

        val error = model.saveEdit(model.state.value.list.items.first(), TransactionUpdate("Coffee", "c2"), CategoryRuleInput("STARBUCKS", "c2", 0))
        advanceUntilIdle()

        assertNull(error)
        assertEquals("Transaction updated, but the rule could not be added: A rule for \"STARBUCKS\" already exists.", messages.single().text)
    }

    @Test
    fun `deleting offers to undo, which restores it`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        val transaction = model.state.value.list.items.first()

        model.delete(transaction)
        advanceUntilIdle()
        messages.single().action!!.invoke()
        advanceUntilIdle()

        assertEquals("Undo", messages.first().actionLabel)
        assertEquals(listOf("deleteTransaction" to transaction.id, "restoreTransaction" to transaction.id), api.sent)
        assertEquals("Restored “${transaction.description}”", messages.last().text)
    }

    @Test
    fun `the deleted view lists hidden transactions, without totals`() = runTest(dispatcher) {
        val (model, _) = model()
        advanceUntilIdle()
        val breakdowns = api.breakdownInputs.size

        model.toggleDeleted()
        advanceUntilIdle()

        assertEquals(true, api.transactionFilters.last().hidden)
        assertEquals(breakdowns, api.breakdownInputs.size)
        model.toggleDeleted()
        advanceUntilIdle()
        assertEquals(TransactionFilter().hidden, api.transactionFilters.last().hidden)
    }

    @Test
    fun `an import that looks like another account's can go ahead on purpose`() = runTest(dispatcher) {
        val (model, messages) = model()
        api.refusing["importStatement"] = FakeFinanceApi.conflict("4 of the 5 transactions are already in \"Visa\".", "account-mismatch")

        val first = model.import("s.csv", "x".toByteArray(), "a1", confirmed = false)
        api.refusing.clear()
        val second = model.import("s.csv", "x".toByteArray(), "a1", confirmed = true)
        advanceUntilIdle()

        assertEquals(ImportOutcome.Mismatch("4 of the 5 transactions are already in \"Visa\"."), first)
        assertEquals(ImportOutcome.Imported(ImportResult(read = 10, added = 7, skipped = 3)), second)
        assertEquals(listOf("a1", "true"), api.sent.last().second)
        // Each file says nothing; the sheet sums them up once they are all in.
        assertTrue(messages.isEmpty())
        model.imported(listOf((second as ImportOutcome.Imported).result))
        advanceUntilIdle()
        assertEquals("Imported 7 new transactions (3 already imported)", messages.single().text)
    }

    @Test
    fun `closing the import while it uploads lets the files finish, and says how it went`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        api.importAnswers += ImportResult(read = 2, added = 2, skipped = 0)
        api.importAnswers += ImportResult(read = 3, added = 1, skipped = 2)
        api.slow["importStatement"] = 100

        model.openImport(listOf(PickedFile("May.csv", ByteArray(10)), PickedFile("June.csv", ByteArray(10))))
        model.importStatements("a1")
        model.closeImport()
        val before = reloads()
        advanceUntilIdle()

        assertEquals(2, api.sent.count { it.first == "importStatement" })
        assertEquals("Imported 3 new transactions from 2 files (2 already imported)", messages.single().text)
        assertTrue(reloads() > before)
        assertTrue(!model.importState.value.open && model.importState.value.files.isEmpty())
    }

    @Test
    fun `closed midway, the files that didn't go in are named`() = runTest(dispatcher) {
        val (model, messages) = model()
        advanceUntilIdle()
        api.importAnswers += ImportResult(read = 2, added = 2, skipped = 0)
        api.importAnswers += ApiError.Http(400, "Bad Request", null, null, mapOf("file" to listOf("This PDF can't be read.")))
        api.slow["importStatement"] = 100

        model.openImport(listOf(PickedFile("May.csv", ByteArray(10)), PickedFile("scan.pdf", ByteArray(10))))
        model.importStatements("a1")
        model.closeImport()
        advanceUntilIdle()

        assertEquals("Imported 2 new transactions. Not imported: scan.pdf.", messages.single().text)
    }

    @Test
    fun `the import's files outlast the sheet being recreated`() = runTest(dispatcher) {
        val (model, _) = model()
        advanceUntilIdle()
        model.openImport(listOf(PickedFile("May.csv", ByteArray(10))))
        model.chooseImportAccount("a1")

        // A rotation recreates the sheet; what it shows is the model's.
        assertEquals(listOf("May.csv"), model.importState.value.files.map { it.file.name })
        assertEquals("a1", model.importState.value.accountId)
        assertTrue(model.importState.value.open)
    }
}
