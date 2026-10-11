package com.guibilar.financeinsights.ui.settings

import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.ui.common.EmptyState
import com.guibilar.financeinsights.ui.common.ErrorState
import com.guibilar.financeinsights.ui.common.LoadState
import com.guibilar.financeinsights.ui.common.Skeleton
import com.guibilar.financeinsights.ui.common.rememberOpenItem
import kotlinx.coroutines.launch

private fun usage(tag: Tag) = "${tag.transactionCount} ${if (tag.transactionCount == 1) "transaction" else "transactions"}"

/** Your own labels across categories, like a trip or what's tax deductible: add, rename, delete, see what has one. */
@Composable
fun TagsScreen(model: TagsViewModel, contentPadding: PaddingValues, onBack: () -> Unit, openTransactions: (Tag) -> Unit) {
    val state by model.state.collectAsState()
    LifecycleResumeEffect(model) {
        model.resumed()
        onPauseOrDispose {}
    }
    val tags = state.valueOrNull.orEmpty()
    var editing by rememberOpenItem(tags) { it.id }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberOpenItem(tags) { it.id }

    SettingsScaffold("Tags", onBack, contentPadding, model.messages, addLabel = "New tag", onAdd = { adding = true }) {
        Text(
            "Your own labels across categories, like a trip or what's tax deductible. Tag a transaction while editing it, or many at once by selecting them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        when (val current = state) {
            LoadState.Loading -> Skeleton(lines = 6, lineHeight = 24.dp, modifier = Modifier.padding(16.dp))
            is LoadState.Failed -> ErrorState(current.message, model::reload)
            is LoadState.Loaded -> if (current.value.isEmpty()) EmptyState("No tags yet.") else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
                    items(current.value, key = { it.id }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = tag }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Tag, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tag.name, fontWeight = FontWeight.Medium)
                                Text(usage(tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 46.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val tag = editing
        TagSheet(
            tag = tag,
            onSave = { model.save(tag, it) },
            onShow = openTransactions,
            onDelete = { deleting = it },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${tag.name}”?") },
            text = {
                Text(
                    if (tag.transactionCount > 0) "It comes off the ${usage(tag)} that have it. The transactions stay as they are."
                    else "No transaction has it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    model.delete(tag)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A tag's name; for one there is, its transactions and deleting it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    tag: Tag?,
    onSave: suspend (String) -> String?,
    onShow: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(tag?.name ?: "") }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val nameError = when {
        name.isBlank() -> "Enter a name."
        name.trim().length > TextLimits.TAG_NAME -> "At most ${TextLimits.TAG_NAME} characters."
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (tag == null) "New tag" else "Edit tag", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                placeholder = { Text("e.g. Vacation 2026") },
                isError = tried && nameError != null, supportingText = nameError?.takeIf { tried }?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !saving,
                onClick = {
                    tried = true
                    if (nameError != null) return@Button
                    if (tag != null && name.trim() == tag.name) {
                        onDismiss()
                        return@Button
                    }
                    saving = true
                    failure = null
                    scope.launch {
                        failure = onSave(name)
                        saving = false
                        if (failure == null) {
                            // Closed even if the sheet leaves the screen while it slides away.
                            try {
                                sheet.hide()
                            } finally {
                                onDismiss()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save") }
            if (tag != null) {
                TextButton(onClick = {
                    onDismiss()
                    onShow(tag)
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Show its ${usage(tag)}")
                }
                TextButton(onClick = {
                    onDelete(tag)
                    onDismiss()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete tag", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
