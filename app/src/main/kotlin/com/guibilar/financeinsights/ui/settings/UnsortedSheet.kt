package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.Money
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.UncategorisedReport
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch

/**
 * The merchants left in Other because no rule names them, most transactions first: picking a category adds a rule for
 * the keyword (which can be edited first) and applies the rules, sorting all of that merchant's at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnsortedSheet(report: UncategorisedReport, categories: List<Category>, model: RulesViewModel, onDismiss: () -> Unit) {
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
