package com.guibilar.financeinsights.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.home.HomeApi
import com.guibilar.financeinsights.core.home.ItemSummary
import com.guibilar.financeinsights.core.home.NO_ROOM
import com.guibilar.financeinsights.core.home.NO_ROOM_NAME
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.core.home.RoomDetails
import com.guibilar.financeinsights.core.logic.DataChanges
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.UiMessage
import com.guibilar.financeinsights.ui.common.load
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomState(
    /** The room; null for the items without a room. */
    val room: Room? = null,
    /** The items of the room, by name. */
    val items: LoadState<List<ItemSummary>> = LoadState.Loading,
    val busy: Boolean = false,
    /** The room was deleted: the page closes. */
    val deleted: Boolean = false,
) {
    val name get() = room?.name ?: NO_ROOM_NAME
}

/**
 * One room: its items (all of them, a few pages at most), rename, and delete (only when it has no items). [id] is a
 * room id, or [NO_ROOM] for the items without a room (which can't be renamed or deleted).
 */
class RoomViewModel(private val api: HomeApi, val id: String) : ViewModel() {
    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var shown = false

    /** A real room: it can be renamed and deleted. */
    val isRoom get() = id != NO_ROOM

    init {
        reload()
    }

    /** Back on the page (after an item changed): the items again, kept if that fails. */
    fun resumed() {
        if (!shown) {
            shown = true
            return
        }
        reload(quiet = true)
    }

    fun reload(quiet: Boolean = false) {
        viewModelScope.launch {
            coroutineScope {
                val room = async { if (isRoom) apiCall { api.rooms().firstOrNull { it.id == id } }.getOrNull() else null }
                val items = async { load(LOAD_FAILED) { allItems() } }
                val loaded = items.await()
                _state.update { state ->
                    state.copy(
                        room = room.await() ?: state.room,
                        items = if (loaded is LoadState.Failed && quiet && state.items is LoadState.Loaded) state.items else loaded,
                    )
                }
            }
        }
    }

    private suspend fun allItems(): List<ItemSummary> {
        val items = mutableListOf<ItemSummary>()
        var page = 1
        do {
            val loaded = api.items(room = id, page = page, pageSize = HomeViewModel.PAGE_SIZE)
            items += loaded.items
            page++
        } while (loaded.hasNextPage && page <= HomeViewModel.MAX_PAGES)
        return items
    }

    /** Renames the room; null when done, or why not (for the dialog). */
    suspend fun rename(name: String): String? {
        if (!isRoom || _state.value.busy) return null
        _state.update { it.copy(busy = true) }
        val result = apiCall { api.updateRoom(id, RoomDetails(name.trim())) }
        _state.update { it.copy(busy = false) }
        result.apiError?.let { return writeError(it, "Could not rename the room.") }
        _state.update { it.copy(room = result.getOrThrow()) }
        DataChanges.changed()
        _messages.trySend(UiMessage("Renamed"))
        reload(quiet = true)
        return null
    }

    /** Deletes the room. The server refuses a room with items (409): the message says why. */
    fun delete() {
        if (!isRoom || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = apiCall { api.deleteRoom(id) }
            val error = result.apiError
            _state.update { it.copy(busy = false, deleted = error == null) }
            if (error != null) {
                _messages.trySend(UiMessage(writeError(error, "Could not delete the room.")))
            } else {
                DataChanges.changed()
            }
        }
    }

    companion object {
        private const val LOAD_FAILED = "Could not load the items."
    }
}
