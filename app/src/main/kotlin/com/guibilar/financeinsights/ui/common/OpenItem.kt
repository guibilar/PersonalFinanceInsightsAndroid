package com.guibilar.financeinsights.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.reflect.KProperty

/**
 * The item a sheet or dialog is open on, kept by id so it stays open when the phone rotates (or the theme
 * changes): the item is looked up again in [items], so it is the latest copy, and the sheet closes if it is gone.
 */
class OpenItem<T>(private val openId: MutableState<String?>, private val items: List<T>, private val id: (T) -> String) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        openId.value?.let { key -> items.firstOrNull { id(it) == key } }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        openId.value = value?.let(id)
    }
}

@Composable
fun <T> rememberOpenItem(items: List<T>, id: (T) -> String): OpenItem<T> {
    val openId = rememberSaveable { mutableStateOf<String?>(null) }
    return OpenItem(openId, items, id)
}
