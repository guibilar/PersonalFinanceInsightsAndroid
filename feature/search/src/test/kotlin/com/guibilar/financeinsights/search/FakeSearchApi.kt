package com.guibilar.financeinsights.search

import java.io.IOException

/** The Search API on fixed hits for "dishwasher". Each call is recorded; any can fail. */
class FakeSearchApi : SearchApi {
    val sent = mutableListOf<Pair<String, String?>>()
    var failing = false

    override suspend fun search(q: String, module: String?): SearchResult {
        sent += q to module
        if (failing) throw IOException("offline")
        if (!q.contains("dishwasher")) return SearchResult(q, 0, listOf(SearchModule("Money"), SearchModule("Tasks")))
        val tasks = (1..7).map { SearchHit("tasks.task.k$it", "Tasks", "task", if (it == 1) "Clean the dishwasher filter" else "Dishwasher job $it", "Open", "2026-11-01", null, "/tasks/k$it") }
        val modules = listOf(
            SearchModule("Money", "Complete", 1, listOf(SearchHit("money.transaction.t1", "Money", "transaction", "Home Depot", "Household · CIBC Visa", "2025-09-08", -899.0, "/money/transactions?id=t1"))),
            SearchModule("Documents", "Complete", 1, listOf(SearchHit("documents.document.d1", "Documents", "document", "Warranty card", "Warranty", null, null, "/documents/d1"))),
            SearchModule("Tasks", "Complete", 7, if (module == "Tasks") tasks else tasks.take(1)),
            SearchModule("Home", "Complete", 1, listOf(SearchHit("home.item.i1", "Home", "item", "Dishwasher", "Kitchen", "2025-09-08", null, "/home/items/i1"))),
            SearchModule("People", "Failed"),
        ).filter { module == null || it.module == module }
        return SearchResult(q, modules.sumOf { it.total }, modules)
    }
}
