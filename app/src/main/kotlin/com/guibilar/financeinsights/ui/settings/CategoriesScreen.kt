package com.guibilar.financeinsights.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.CATEGORY_PALETTE
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.logic.freeCategoryColor
import com.guibilar.financeinsights.core.model.Category
import com.guibilar.financeinsights.core.model.CategoryInput
import com.guibilar.financeinsights.ui.common.ColorDot
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.theme.parseHexColor
import kotlinx.coroutines.launch

private fun usage(category: Category) =
    "${category.transactionCount} ${if (category.transactionCount == 1) "transaction" else "transactions"} · " +
        "${category.ruleCount} ${if (category.ruleCount == 1) "rule" else "rules"}"

@Composable
fun CategoriesScreen(model: CategoriesViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val state by model.state.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val categories = state.valueOrNull.orEmpty()

    SettingsScaffold("Categories", onBack, contentPadding, model.messages, addLabel = "New category", onAdd = { adding = true }) {
        Text(
            "Renaming or recolouring a category changes it everywhere. Deleting one moves its transactions and rules to another.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        when (val current = state) {
            LoadState.Loading -> Skeleton(lines = 8, lineHeight = 24.dp, modifier = Modifier.padding(16.dp))
            is LoadState.Failed -> ErrorState(current.message, model::reload)
            is LoadState.Loaded -> if (current.value.isEmpty()) EmptyState("No categories yet.") else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(current.value, key = { it.id }) { category ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = category }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(parseHexColor(category.color), size = 14.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(category.name, fontWeight = FontWeight.Medium)
                                Text(usage(category), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (category.builtInReason != null) {
                                Icon(Icons.Outlined.Lock, "Built in", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 42.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val category = editing
        CategorySheet(
            category = category,
            used = categories.map { it.color },
            onSave = { model.save(category, it) },
            onDelete = { deleting = it },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    deleting?.let { category ->
        DeleteCategoryDialog(category, categories, onConfirm = { target -> model.delete(category, target) }, onDismiss = { deleting = null })
    }
}

/** A category: its name and colour (from the palette, or any "#RRGGBB"), and deleting it unless it is built in. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    category: Category?,
    used: List<String>,
    onSave: suspend (CategoryInput) -> String?,
    onDelete: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(category?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(category?.color?.uppercase() ?: freeCategoryColor(used)) }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val nameError = when {
        name.isBlank() -> "Enter a name."
        name.length > TextLimits.CATEGORY_NAME -> "At most ${TextLimits.CATEGORY_NAME} characters."
        else -> null
    }
    val colorError = if (Regex("^#[0-9A-Fa-f]{6}$").matches(color)) null else "A colour looks like #1F76B5."
    val showColorError = colorError != null && (tried || color.length >= 7)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (category == null) "New category" else "Edit category", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                isError = tried && nameError != null, supportingText = nameError?.takeIf { tried }?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CATEGORY_PALETTE.forEach { swatch ->
                    val chosen = swatch.equals(color, ignoreCase = true)
                    val fill = parseHexColor(swatch)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable(role = Role.RadioButton) { color = swatch }
                            .semantics {
                                contentDescription = swatch
                                selected = chosen
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chosen) Icon(Icons.Outlined.Check, null, tint = if (fill.luminance() > 0.5f) Color.Black else Color.White)
                    }
                }
            }
            OutlinedTextField(
                value = color,
                // "1f76b5" pasted or typed without the "#" is the same colour.
                onValueChange = { typed ->
                    val text = typed.trim().uppercase()
                    color = (if (text.isEmpty() || text.startsWith("#")) text else "#$text").take(7)
                },
                label = { Text("Or any colour") },
                leadingIcon = { ColorDot(if (colorError == null) parseHexColor(color) else Color.Transparent, size = 16.dp) },
                // Not while it is still being typed: only once it is complete, or on Save.
                isError = showColorError,
                supportingText = colorError?.takeIf { showColorError }?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (nameError != null || colorError != null) return@Button
                    saving = true
                    failure = null
                    scope.launch {
                        failure = onSave(CategoryInput(name.trim(), color))
                        saving = false
                        if (failure == null) {
                            sheet.hide()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save") }
            if (category != null) {
                if (category.builtInReason != null) {
                    Text(category.builtInReason!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = {
                        onDelete(category)
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete category", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Deleting a category: what used it (transactions and rules) moves to another, Other unless chosen. */
@Composable
private fun DeleteCategoryDialog(category: Category, categories: List<Category>, onConfirm: (Category?) -> Unit, onDismiss: () -> Unit) {
    val targets = categories.filter { it.id != category.id }
    var target by remember { mutableStateOf(targets.firstOrNull { it.isFallback } ?: targets.firstOrNull()) }
    val inUse = category.transactionCount + category.ruleCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “${category.name}”?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (inUse) {
                    val parts = listOfNotNull(
                        category.transactionCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "transaction" else "transactions"}" },
                        category.ruleCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "rule" else "rules"}" },
                    )
                    val one = category.transactionCount + category.ruleCount == 1
                    Text("Its ${parts.joinToString(" and ")} ${if (one) "moves" else "move"} to")
                    // Radio rows, not a dropdown: a dialog measures its content in a way a dropdown field can't take.
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                        targets.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = option.id == target?.id, role = Role.RadioButton) { target = option }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = option.id == target?.id, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                ColorDot(parseHexColor(option.color))
                                Spacer(Modifier.width(8.dp))
                                Text(option.name)
                            }
                        }
                    }
                } else {
                    Text("No transactions or rules use it.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm(if (inUse) target else null)
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
