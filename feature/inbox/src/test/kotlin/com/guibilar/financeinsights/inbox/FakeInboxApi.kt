package com.guibilar.financeinsights.inbox

import java.io.IOException

/** The Inbox API on notes in memory: open ones newest first. Each call is recorded; any can fail. */
class FakeInboxApi(notes: List<InboxNote> = samples) : InboxApi {
    val notes = notes.toMutableList()
    val sent = mutableListOf<Pair<String, Any?>>()
    val failing = mutableSetOf<String>()
    private var next = 10

    private fun <T> call(name: String, body: Any?, answer: () -> T): T {
        sent += name to body
        if (name in failing) throw IOException("offline")
        return answer()
    }

    private fun replace(note: InboxNote): InboxNote {
        notes.replaceAll { if (it.id == note.id) note else it }
        return note
    }

    override suspend fun notes(done: Boolean, pageSize: Int) = call("notes", done) {
        val list = notes.filter { (it.doneAt != null) == done }
        InboxPage(list, list.size)
    }

    override suspend fun add(text: InboxText) = call("add", text.text) {
        InboxNote("n${next++}", text.text, NOW, NOW).also { notes.add(0, it) }
    }

    override suspend fun update(id: String, text: InboxText) = call("update", id to text.text) {
        replace(notes.first { it.id == id }.copy(text = text.text))
    }

    override suspend fun done(id: String, done: InboxDone) = call("done", id to done.sortedTo) {
        replace(notes.first { it.id == id }.copy(doneAt = NOW, sortedTo = done.sortedTo))
    }

    override suspend fun reopen(id: String) = call("reopen", id) {
        replace(notes.first { it.id == id }.copy(doneAt = null, sortedTo = null))
    }

    override suspend fun delete(id: String) = call("delete", id) {
        notes.removeAll { it.id == id }
        Unit
    }

    companion object {
        const val NOW = "2026-10-10T18:22:00Z"

        val samples = listOf(
            InboxNote("n1", "Call the plumber\nAbout the leak under the sink", "2026-10-10T14:22:00Z", "2026-10-10T14:22:00Z"),
            InboxNote("n2", "https://example.com/lease-renewal", "2026-10-09T09:05:00Z", "2026-10-09T09:05:00Z"),
            InboxNote("n3", "Ask Ana about the trip", "2026-10-08T20:00:00Z", "2026-10-08T20:00:00Z", "2026-10-09T08:00:00Z", "Journal"),
        )
    }
}
