package com.guibilar.financeinsights.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guibilar.financeinsights.home.HomeHost
import com.guibilar.financeinsights.ui.common.FitText
import com.guibilar.financeinsights.ui.common.ReadableColumn

/** The long side of the photo preview, in pixels. */
private const val PREVIEW_SIDE = 1200

/**
 * A new item (design canvas, "Home", the + button): a photo first when [start] says so (the camera or the photo
 * picker), then the form. Save adds the item and uploads the photo; [created] then opens the new item, with a notice
 * when the photo did not go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewItemScreen(
    model: NewItemViewModel,
    host: HomeHost,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    created: (id: String, notice: String?) -> Unit,
    start: PhotoSource? = null,
) {
    val state by model.state.collectAsState()
    // The categories in use (T89), for the suggestions of the form.
    val categories by produceState(emptyList<String>(), model) { value = model.categories() }
    var draft by rememberSaveable(stateSaver = ItemDraftSaver) { mutableStateOf(model.initial) }
    val pickers = rememberPhotoPickers(host, onPreparing = model::preparing, onPicked = model::photo)
    // The camera or the picker opens once, not again after a rotation.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start != null && !started) {
            started = true
            pickers.launch(start)
        }
    }
    LaunchedEffect(state.created) { state.created?.let { created(it, state.notice) } }
    val preview = remember(state.photo) { state.photo?.let { host.decodePhoto(it.bytes, PREVIEW_SIDE) } }

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = { FitText("Add item", fontWeight = FontWeight.SemiBold, minFontSize = 14.sp) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } },
        )
        ReadableColumn {
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Reading the photo…", style = MaterialTheme.typography.bodyMedium)
                    }
                    state.photo != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (preview != null) {
                            Image(
                                preview.asImageBitmap(),
                                contentDescription = "The photo of the item",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                            )
                        } else {
                            Text("A photo is ready to go with the item.", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = model::removePhoto) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remove the photo")
                        }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickers.launch(PhotoSource.Camera) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Take a photo")
                        }
                        OutlinedButton(onClick = { pickers.launch(PhotoSource.Photos) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pick a photo")
                        }
                    }
                }
                ItemForm(draft, state.rooms, onChange = { draft = it }, categories = categories)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = { model.save(draft) }, enabled = draft.canSave && !state.saving && !state.preparing) {
                        Text(if (state.saving) "Saving…" else "Save")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
