package com.guibilar.financeinsights.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Whether a floating "Add" button shows its label: it shrinks to its icon while the content scrolls down, so it
 * covers less of what is read (a button, an amount), and grows back on scrolling up. Watch the content by giving
 * [connection] to a `nestedScroll` around it.
 */
class FabExpansion {
    var expanded by mutableStateOf(true)
        private set

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -1f) expanded = false else if (available.y > 1f) expanded = true
            return Offset.Zero
        }
    }
}

@Composable
fun rememberFabExpansion() = remember { FabExpansion() }
