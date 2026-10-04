package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.guibilar.financeinsights.core.logic.FLOW_RULE_FLOWS
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.description
import com.guibilar.financeinsights.core.logic.ruleScope
import com.guibilar.financeinsights.core.model.CategoryReference
import com.guibilar.financeinsights.core.model.CategoryRule
import com.guibilar.financeinsights.core.model.CategoryRuleInput
import com.guibilar.financeinsights.core.model.Flow
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.core.model.FlowRuleInput
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Placeholder
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberOpenItem
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableStateMapOf
import com.guibilar.financeinsights.core.model.UncategorisedReport
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.logic.Money

private const val APPLY_CONFIRMATION =
    "Every transaction gets its category and flow from the current rules again. Categories and flows you picked by " +
        "hand, and the categories of savings transactions, are left as they are."

/** The rules, in two tabs: categories (checked in order) and money in and transfers (flow rules). */
@Composable
fun RulesScreen(model: RulesViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    // Saved, so a sheet stays open (with what was typed) when the phone rotates.
    var editingRule by rememberOpenItem(state.rules.valueOrNull.orEmpty()) { it.id }
    var addingRule by rememberSaveable { mutableStateOf(false) }
    var editingFlow by rememberOpenItem(state.flowRules.valueOrNull.orEmpty()) { it.id }
    var addingFlow by rememberSaveable { mutableStateOf(false) }
    var confirmApply by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = "Rules",
        onBack = onBack,
        contentPadding = contentPadding,
        messages = model.messages,
        addLabel = "New rule",
        onAdd = { if (tab == 0) addingRule = true else addingFlow = true },
        actions = { IconButton(onClick = { confirmApply = true }) { Icon(Icons.Outlined.Refresh, "Apply the rules to transactions") } },
    ) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Categories") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Money in and transfers") })
        }
        if (tab == 0) CategoryRules(state, model, onEdit = { editingRule = it }) else FlowRules(state, model, onEdit = { editingFlow = it })
    }

    if (addingRule || editingRule != null) {
        val rule = editingRule
        CategoryRuleSheet(
            rule = rule,
            categories = state.categories.map { it.reference },
            onSave = { model.saveRule(rule, it) },
            onDelete = { model.deleteRule(it) },
            onDismiss = {
                addingRule = false
                editingRule = null
            },
        )
    }
    if (addingFlow || editingFlow != null) {
        val rule = editingFlow
        FlowRuleSheet(
            rule = rule,
            onSave = { model.saveFlowRule(rule, it) },
            onDelete = { model.deleteFlowRule(it) },
            onDismiss = {
                addingFlow = false
                editingFlow = null
            },
        )
    }
    if (confirmApply) {
        ConfirmDelete("Apply the rules?", APPLY_CONFIRMATION, onConfirm = { model.applyRules() }, onDismiss = { confirmApply = false }, confirmLabel = "Apply rules", destructive = false)
    }
}

@Composable
private fun CategoryRules(state: RulesState, model: RulesViewModel, onEdit: (CategoryRule) -> Unit) {
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
                                        onDrag(amount.y)
                                    },
                                    onDragEnd = { onDrop() },
                                    onDragCancel = { onDrop() },
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

@Composable
private fun FlowRules(state: RulesState, model: RulesViewModel, onEdit: (FlowRule) -> Unit) {
    val rules = state.flowRules
    Column {
        Text(
            "What a transaction counts as when its description contains a keyword; the longest keyword wins. Without " +
                "a rule, money out is spending, money in is a refund on a credit card and income elsewhere, and savings " +
                "movements are transfers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        when (rules) {
            LoadState.Loading -> Skeleton(lines = 6, lineHeight = 24.dp, modifier = Modifier.padding(16.dp))
            is LoadState.Failed -> ErrorState(rules.message, model::reload)
            is LoadState.Loaded -> if (rules.value.isEmpty()) EmptyState("No flow rules yet.") else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(rules.value, key = { it.id }) { rule ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onEdit(rule) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(rule.keyword, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text(rule.flow.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private fun keywordError(keyword: String, max: Int) = when {
    keyword.isBlank() -> "Enter a keyword."
    keyword.length > max -> "At most $max characters."
    else -> null
}

/** A category rule: its keyword and category, where a new one goes (first or last), and deleting it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRuleSheet(
    rule: CategoryRule?,
    categories: List<CategoryReference>,
    onSave: suspend (CategoryRuleInput) -> String?,
    onDelete: (CategoryRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyword by rememberSaveable { mutableStateOf(rule?.keyword ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(rule?.category?.id) }
    var first by rememberSaveable { mutableStateOf(true) }
    val options = if (rule != null && categories.none { it.id == rule.category.id }) listOf(rule.category) + categories else categories
    val category = options.firstOrNull { it.id == categoryId }
    RuleSheet(
        title = if (rule == null) "New rule" else "Edit rule",
        error = keywordError(keyword, TextLimits.CATEGORY_RULE_KEYWORD) ?: if (category == null) "Choose a category." else null,
        onSave = { onSave(CategoryRuleInput(keyword.trim(), category!!.id, if (rule == null && first) 0 else null)) },
        onDelete = rule?.let { { onDelete(it) } },
        deleteMessage = rule?.let {
            "Transactions containing “${it.keyword}” will no longer be categorised as ${it.category.name} by this rule. " +
                "Transactions already imported keep their category until the rules are applied again."
        },
        onDismiss = onDismiss,
    ) {
        KeywordField(keyword, { keyword = it }, "Description contains", "Case doesn't matter, e.g. no frills")
        DropdownPicker(
            label = "Sets the category",
            options = options,
            selected = category,
            text = { it?.name ?: "Choose a category" },
            dot = { it?.let { c -> parseHexColor(c.color) } },
            onPick = { categoryId = it.id },
        )
        if (rule == null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to "Check it first", false to "Check it last").forEachIndexed { index, (value, label) ->
                    SegmentedButton(first == value, onClick = { first = value }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { FitText(label) }
                }
            }
            Text(
                if (first) "It decides before every other rule." else "Other rules that match decide first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A flow rule: its keyword and what it makes a transaction count as. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlowRuleSheet(rule: FlowRule?, onSave: suspend (FlowRuleInput) -> String?, onDelete: (FlowRule) -> Unit, onDismiss: () -> Unit) {
    var keyword by rememberSaveable { mutableStateOf(rule?.keyword ?: "") }
    var flow by rememberSaveable { mutableStateOf(rule?.flow ?: Flow.Transfer) }
    RuleSheet(
        title = if (rule == null) "New flow rule" else "Edit flow rule",
        error = keywordError(keyword, TextLimits.FLOW_RULE_KEYWORD),
        onSave = { onSave(FlowRuleInput(keyword.trim(), flow)) },
        onDelete = rule?.let { { onDelete(it) } },
        deleteMessage = rule?.let { "Transactions containing “${it.keyword}” will no longer count as ${it.flow.label.lowercase()} by this rule once the rules are applied again." },
        onDismiss = onDismiss,
    ) {
        KeywordField(keyword, { keyword = it }, "Description contains", "e.g. payment thank you")
        Text("Counts as", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FLOW_RULE_FLOWS.forEach { option -> FilterChip(flow == option, onClick = { flow = option }, label = { Text(option.label) }) }
        }
        Text("${flow.description} ${flow.ruleScope}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeywordField(value: String, onChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Placeholder(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The sheet both kinds of rule use: fields, Save, and Delete for an existing rule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    title: String,
    error: String?,
    onSave: suspend () -> String?,
    onDelete: (() -> Unit)?,
    deleteMessage: String?,
    onDismiss: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            fields()
            val shown = failure ?: error?.takeIf { tried }
            if (shown != null) Text(shown, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (error != null) return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        failure = onSave()
                        saving = false
                        if (failure == null) {
                            sheet.hide()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save") }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete rule", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDelete("Delete rule?", deleteMessage ?: "", onConfirm = {
            onDelete()
            onDismiss()
        }, onDismiss = { confirmDelete = false })
    }
}

/**
 * The merchants left in Other because no rule names them, most transactions first: picking a category adds a rule for
 * the keyword (which can be edited first) and applies the rules, sorting all of that merchant's at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsortedSheet(report: UncategorisedReport, categories: List<Category>, model: RulesViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keywords = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Sort what's left in Other", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Pick a category, or keep it in Other: a rule is added for the keyword and applied to all of them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (report.merchants.isEmpty()) EmptyState("Nothing left to sort.")
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding().imePadding()) {
            items(report.merchants, key = { it.keyword }) { merchant ->
                val keyword = keywords[merchant.keyword] ?: merchant.keyword
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keywords[merchant.keyword] = it.take(TextLimits.CATEGORY_RULE_KEYWORD) },
                        label = { Text("Descriptions containing") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${merchant.count} ${if (merchant.count == 1) "transaction" else "transactions"} · ${Money.format(merchant.spending)} · " +
                            "like “${merchant.examples.firstOrNull().orEmpty()}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownPicker(
                        label = "Category",
                        options = categories,
                        selected = null,
                        // Other comes last, as keeping them where they are: a rule into Other says they belong there.
                        text = { c -> c?.let { if (it.isFallback) "Keep in ${it.name}" else it.name } ?: if (busy == merchant.keyword) "Adding the rule…" else "Categorise as…" },
                        dot = { it?.let { c -> parseHexColor(c.color) } },
                        enabled = busy == null,
                        onPick = { category ->
                            busy = merchant.keyword
                            error = null
                            scope.launch {
                                error = model.sortMerchant(keyword, category)
                                busy = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
