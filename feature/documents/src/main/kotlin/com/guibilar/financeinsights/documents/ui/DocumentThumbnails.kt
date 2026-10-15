package com.guibilar.financeinsights.documents.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.guibilar.financeinsights.core.api.ApiError
import com.guibilar.financeinsights.core.api.apiCall
import com.guibilar.financeinsights.core.api.apiError
import com.guibilar.financeinsights.core.documents.DocumentsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The thumbnails of document files (T61), from the server, kept in memory while the app runs. A file without a
 * thumbnail (404: the server cannot preview it) is remembered as "none", so a list does not ask again. Other errors (no
 * network) are not remembered: the next look tries again.
 */
class DocumentThumbnails(private val api: DocumentsApi) {
    private sealed interface Entry {
        data class Found(val image: ImageBitmap) : Entry
        data object None : Entry
    }

    private val cache = LruCache<String, Entry>(MAX_ENTRIES)

    /** The thumbnail of the file, or null when it has none or did not load. */
    suspend fun load(documentId: String, fileId: String): ImageBitmap? {
        when (val entry = cache.get(fileId)) {
            is Entry.Found -> return entry.image
            Entry.None -> return null
            null -> Unit
        }
        val result = apiCall { api.thumbnail(documentId, fileId).use { body -> withContext(Dispatchers.IO) { body.bytes() } } }
        val bytes = result.getOrNull()
        if (bytes == null) {
            if ((result.apiError as? ApiError.Http)?.status == 404) cache.put(fileId, Entry.None)
            return null
        }
        val image = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        cache.put(fileId, image?.let { Entry.Found(it) } ?: Entry.None)
        return image
    }

    private companion object {
        /** About 64 × 480 × 480 × 4 bytes at most (60 MB); most thumbnails are smaller. */
        const val MAX_ENTRIES = 64
    }
}

/**
 * A square thumbnail that [load] gives, or [fallback] (the icon of the kind) while it loads and when there is none. The
 * key is the file: another file loads again.
 */
@Composable
fun DocumentThumbnail(fileId: String, load: suspend () -> ImageBitmap?, fallback: @Composable () -> Unit) {
    val image by produceState<ImageBitmap?>(null, fileId) { value = load() }
    val shown = image
    if (shown == null) {
        fallback()
    } else {
        Image(
            shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // The top of a page: its heading tells most.
            alignment = Alignment.TopCenter,
            // An outline, so a white page shows on a light card.
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).testTag("thumbnail"),
        )
    }
}
