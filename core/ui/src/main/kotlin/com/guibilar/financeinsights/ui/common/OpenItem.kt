package com.guibilar.financeinsights.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.reflect.KProperty

/**
 * The item a sheet or dialog is open on, kept by id so it stays open when the phone rotates (or the theme
 * changes): the item is looked up again in [items], so it is the latest copy. While it is open but not in [items]
 * (a reload brought back only the first page, or a filter it no longer passes), the copy it opened on is kept, so
 * the sheet doesn't close on what was typed, to open again by itself once the item is back.
 */
class OpenItem<T>(
    private val openId: MutableState<String?>,
    private val items: List<T>,
    private val id: (T) -> String,
    private val last: Last<T>,
) {
    /** The copy last seen, by id; not saved (after a rotation the lookup in [items] is what there is). */
    class Last<T> {
        var item: T? = null
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        val key = openId.value ?: return null
        val found = items.firstOrNull { id(it) == key }
        if (found != null) last.item = found
        return found ?: last.item?.takeIf { id(it) == key }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        openId.value = value?.let(id)
        last.item = value
    }
}

@Composable
fun <T> rememberOpenItem(items: List<T>, id: (T) -> String): OpenItem<T> {
    val openId = rememberSaveable { mutableStateOf<String?>(null) }
    val last = remember { OpenItem.Last<T>() }
    return OpenItem(openId, items, id, last)
}
