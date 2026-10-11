package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.guibilar.financeinsights.core.logic.description
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.parseHexColor

@Composable
internal fun CategoryRules(state: RulesState, model: RulesViewModel, onEdit: (CategoryRule) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val rules = state.rules
    val unsorted = state.uncategorised?.takeIf { it.transactionCount > 0 }
    Column {
        if (unsorted != null) {
            Card(
                onClick = { sorting = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${unsorted.transactionCount} ${if (unsorted.transactionCount == 1) "transaction is" else "transactions are"} " +
                            "in Other because no rule names ${if (unsorted.merchants.size == 1) "its merchant" else "their merchants"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sort them", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // All sorted: closed for good, not open again by itself the next time something lands in Other.
        LaunchedEffect(unsorted == null) { if (unsorted == null) sorting = false }
        if (sorting && unsorted != null) UnsortedSheet(unsorted, state.categories.filter { !it.isFallback } + state.categories.filter { it.isFallback }, model) { sorting = false }
        Text(
            "The first rule whose keyword is in a description sets the transaction's category.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Placeholder("Search by keyword or category") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when (rules) {
            LoadState.Loading -> Skeleton(lines = 8, lineHeight = 24.dp, modifier = Modifier.padding(16.dp))
            is LoadState.Failed -> ErrorState(rules.message, model::reload)
            is LoadState.Loaded -> {
                val query = search.trim().uppercase()
                // A few hundred rules at most, so the search needs no request.
                val visible = if (query.isEmpty()) rules.value else rules.value.filter {
                    it.keyword.contains(query) || it.category.name.uppercase().contains(query)
                }
                if (rules.value.isEmpty()) EmptyState("No rules yet, so every imported transaction lands in Other.")
                else if (visible.isEmpty()) EmptyState("No rules match “$search”.")
                else {
                    Text(
                        if (query.isEmpty()) "${rules.value.size} rules" else "${visible.size} of ${rules.value.size} rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    RuleList(visible, state.moving, model, onEdit)
                }
            }
        }
    }
}

/**
 * The category rules in order. Each can be dragged by its handle to a new place (a tick as it passes each rule), or
 * moved one place with the arrows, which TalkBack reaches too. Searching, a drag passes the rules that are shown.
 */
@Composable
private fun RuleList(visible: List<CategoryRule>, moving: Boolean, model: RulesViewModel, onEdit: (CategoryRule) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // The order shown while dragging; the server's order again once the move is saved.
    val order = remember(visible) { visible.toMutableStateList() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }

    fun onDrag(delta: Float) {
        val id = dragging ?: return
        offset += delta
        val index = order.indexOfFirst { it.id == id }
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        // Past half of the neighbour: they swap, and the dragged row's offset is kept where the finger is.
        val next = items.firstOrNull { it.index == current.index + 1 }
        val previous = items.firstOrNull { it.index == current.index - 1 }
        if (offset > 0 && next != null && index < order.lastIndex && offset > next.size / 2f) {
            order.add(index + 1, order.removeAt(index))
            offset -= next.size
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (offset < 0 && previous != null && index > 0 && -offset > previous.size / 2f) {
            order.add(index - 1, order.removeAt(index))
            offset += previous.size
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun onDrop() {
        val id = dragging ?: return
        dragging = null
        offset = 0f
        val rule = visible.first { it.id == id }
        val newIndex = order.indexOfFirst { it.id == id }
        // Its new place is that of the rule that was there.
        model.moveTo(rule, visible[newIndex].position)
    }

    // The drag handlers below outlive this composition (a row keeps its node by key): they call these, the latest,
    // so a drag after a search, a save or a reload works on the list shown, not the one there was when it began.
    val dragBy by rememberUpdatedState<(Float) -> Unit>(::onDrag)
    val drop by rememberUpdatedState<() -> Unit>(::onDrop)

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
        items(order, key = { it.id }) { rule ->
            val dragged = rule.id == dragging
            Column(
                if (dragged) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = offset }.shadow(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                } else {
                    Modifier.animateItem()
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(rule) }.padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        "Drag ${rule.keyword} to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .padding(4.dp)
                            .pointerInput(rule.id, moving) {
                                if (moving) return@pointerInput
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = rule.id
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    },
                                    onDragEnd = { drop() },
                                    onDragCancel = { drop() },
                                )
                            },
                    )
                    Text(
                        "${(if (dragged) visible.getOrNull(order.indexOf(rule))?.position ?: rule.position else rule.position) + 1}",
                        Modifier.width(32.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(rule.keyword, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(parseHexColor(rule.category.color), size = 8.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(rule.category.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(
                        onClick = { model.move(visible, rule, -1) },
                        enabled = !moving && dragging == null && model.movePosition(visible, rule, -1) != null,
                    ) { Icon(Icons.Outlined.ArrowUpward, "Move ${rule.keyword} up") }
                    IconButton(
                        onClick = { model.move(visible, rule, 1) },
                        enabled = !moving && dragging == null && model.movePosition(visible, rule, 1) != null,
                    ) { Icon(Icons.Outlined.ArrowDownward, "Move ${rule.keyword} down") }
                }
                HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
