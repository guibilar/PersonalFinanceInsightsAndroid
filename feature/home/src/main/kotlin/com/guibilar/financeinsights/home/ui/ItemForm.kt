package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.home.ItemDraft
import com.guibilar.financeinsights.core.home.NO_ROOM_NAME
import com.guibilar.financeinsights.core.home.Room
import com.guibilar.financeinsights.ui.common.DropdownPicker
import com.guibilar.financeinsights.ui.common.OptionalDateField
import java.time.LocalDate

/** A room choice of the form: a room, or none. */
private data class RoomChoice(val id: String?, val name: String)

/**
 * The details of an item as a form: the name, the room, what it is (category, brand, model, serial number), how many,
 * the purchase (day, price), the value now, the warranty, the tags and the notes. Each change goes to [onChange].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemForm(
    draft: ItemDraft,
    rooms: List<Room>,
    onChange: (ItemDraft) -> Unit,
    modifier: Modifier = Modifier,
    /** The categories in use (T89): the ones that match the typed text show as suggestions. */
    categories: List<String> = emptyList(),
) {
    val choices = listOf(RoomChoice(null, NO_ROOM_NAME)) + rooms.map { RoomChoice(it.id, it.name) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = { Text("Name") },
            supportingText = draft.nameError?.takeIf { draft.name.isNotEmpty() }?.let { { Text(it) } },
            isError = draft.name.isNotEmpty() && draft.nameError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownPicker(
            label = "Room",
            options = choices,
            selected = choices.firstOrNull { it.id == draft.roomId } ?: choices.first(),
            text = { it?.name.orEmpty() },
            onPick = { onChange(draft.copy(roomId = it.id)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextField("Category", draft.category, Modifier.fillMaxWidth(), "Appliance, Electronics, Furniture") { onChange(draft.copy(category = it)) }
        val suggestions = categorySuggestions(categories, draft.category)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { category ->
                    SuggestionChip(onClick = { onChange(draft.copy(category = category)) }, label = { Text(category) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField("Brand", draft.brand, Modifier.weight(1f)) { onChange(draft.copy(brand = it)) }
            TextField("Model", draft.model, Modifier.weight(1f)) { onChange(draft.copy(model = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField("Serial number", draft.serialNumber, Modifier.weight(2f), capitalize = false) { onChange(draft.copy(serialNumber = it)) }
            NumberField("Quantity", draft.quantity, draft.quantityError, KeyboardType.Number, Modifier.weight(1f)) {
                onChange(draft.copy(quantity = it.filter(Char::isDigit).take(6)))
            }
        }
        OptionalDateField("Purchased on", draft.purchasedOn, onChange = { onChange(draft.copy(purchasedOn = it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Price of one", draft.purchasePrice, draft.purchasePriceError, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(draft.copy(purchasePrice = it))
            }
            NumberField("Value now", draft.currentValue, draft.currentValueError, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(draft.copy(currentValue = it))
            }
        }
        Text(
            "The value now counts for the total and the insurance list. Without it, the price counts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OptionalDateField("Warranty until", draft.warrantyUntil, onChange = { onChange(draft.copy(warrantyUntil = it)) })
        OutlinedTextField(
            value = draft.tags,
            onValueChange = { onChange(draft.copy(tags = it)) },
            label = { Text("Tags") },
            supportingText = { Text("Separate tags with commas.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onChange(draft.copy(notes = it)) },
            label = { Text("Notes") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    capitalize: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.Characters),
        modifier = modifier,
    )
}

@Composable
private fun NumberField(label: String, value: String, error: String?, type: KeyboardType, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = modifier,
    )
}

/** Keeps what was typed in the item form when the phone rotates. */
val ItemDraftSaver: Saver<ItemDraft, Any> = listSaver(
    save = {
        listOf(
            it.name, it.roomId, it.category, it.brand, it.model, it.serialNumber, it.quantity, it.purchasedOn?.toString(),
            it.purchasePrice, it.currentValue, it.warrantyUntil?.toString(), it.notes, it.tags,
        )
    },
    restore = {
        ItemDraft(
            name = it[0] as String,
            roomId = it[1] as String?,
            category = it[2] as String,
            brand = it[3] as String,
            model = it[4] as String,
            serialNumber = it[5] as String,
            quantity = it[6] as String,
            purchasedOn = (it[7] as String?)?.let(LocalDate::parse),
            purchasePrice = it[8] as String,
            currentValue = it[9] as String,
            warrantyUntil = (it[10] as String?)?.let(LocalDate::parse),
            notes = it[11] as String,
            tags = it[12] as String,
        )
    },
)

/** The categories in use that fit the typed text (T89): at most 6, without the one that is typed already. */
internal fun categorySuggestions(categories: List<String>, typed: String): List<String> {
    val text = typed.trim()
    return categories
        .filter { it.contains(text, ignoreCase = true) && !it.equals(text, ignoreCase = true) }
        .take(MAX_CATEGORY_SUGGESTIONS)
}

private const val MAX_CATEGORY_SUGGESTIONS = 6
