package com.guibilar.financeinsights.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.logic.TextLimits
import com.guibilar.financeinsights.core.model.Tag
import com.guibilar.financeinsights.core.model.TagReference
import kotlinx.coroutines.launch

/** "Vacation  2026 " as the API keeps it: trimmed, single-spaced. */
fun tidyTagName(name: String) = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")

/** The tag of that name, ignoring case and spaces. */
fun List<TagReference>.named(name: String): TagReference? = tidyTagName(name).lowercase().let { wanted ->
    firstOrNull { it.name.lowercase() == wanted }
}

/**
 * A transaction's tags: chips for the chosen ones (tap to remove), the others to tap and add, and a field that adds
 * one by name, creating it when there is none by that name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    tags: List<TagReference>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onCreate: suspend (String) -> Result<Tag>,
) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val byId = tags.associateBy { it.id }
    val chosen = selected.mapNotNull { byId[it] }.sortedBy { it.name.lowercase() }
    val typed = tidyTagName(text)
    val match = tags.named(typed)
    val offered = tags.filter { it.id !in selected && (typed.isEmpty() || it.name.contains(typed, ignoreCase = true)) }.take(8)

    fun add() {
        if (typed.isEmpty() || creating) return
        if (match != null) {
            if (match.id !in selected) onChange(selected + match.id)
            text = ""
            error = null
            return
        }
        if (typed.length > TextLimits.TAG_NAME) {
            error = "At most ${TextLimits.TAG_NAME} characters."
            return
        }
        creating = true
        error = null
        scope.launch {
            onCreate(typed).fold(
                onSuccess = {
                    onChange(selected + it.id)
                    text = ""
                },
                onFailure = { error = it.message },
            )
            creating = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (chosen.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(selected - tag.id) },
                        label = { Text("#${tag.name}") },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${tag.name}", Modifier.size(16.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            label = { Text("Add a tag") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                if (creating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (typed.isNotEmpty()) TextButton(onClick = ::add) { Text(if (match == null) "Create" else "Add") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (offered.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offered.forEach { tag ->
                    SuggestionChip(
                        onClick = {
                            onChange(selected + tag.id)
                            text = ""
                        },
                        label = { Text("#${tag.name}") },
                    )
                }
            }
        }
    }
}
