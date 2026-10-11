package com.guibilar.financeinsights.ui.settings

import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.description
import com.guibilar.financeinsights.core.model.FlowRule
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberOpenItem

private const val APPLY_CONFIRMATION =
    "Every transaction gets its category and flow from the current rules again. Categories and flows you picked by " +
        "hand, and the categories of savings transactions, are left as they are."

/** The rules, in two tabs: categories (checked in order) and money in and transfers (flow rules). */
@Composable
fun RulesScreen(model: RulesViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
