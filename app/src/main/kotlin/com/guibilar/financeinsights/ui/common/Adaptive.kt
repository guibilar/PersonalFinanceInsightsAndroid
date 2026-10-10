package com.guibilar.financeinsights.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** From this window width on (a small tablet, an unfolded foldable, a phone on its side), the tabs are a rail. */
val MEDIUM_WIDTH = 600.dp

/** From this window width on (a tablet, a large foldable), there is room for two panes side by side. */
val EXPANDED_WIDTH = 840.dp

/** As wide as a column of text and settings gets: wider, lines are hard to follow and rows lose their ends. */
val READABLE_WIDTH = 720.dp

/** As wide as a page of cards side by side gets, so a large tablet doesn't spread them past a glance. */
val WIDE_CONTENT_WIDTH = 1200.dp

/** From this width of content on, a page has room for two columns of cards. */
val TWO_COLUMNS_WIDTH = 720.dp

/** The app window's width. */
@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** [content] in a centred column no wider than [maxWidth]; on a phone, simply the whole width. */
@Composable
fun ReadableColumn(maxWidth: Dp = READABLE_WIDTH, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
