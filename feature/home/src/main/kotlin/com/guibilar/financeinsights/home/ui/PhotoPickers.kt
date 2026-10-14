package com.guibilar.financeinsights.home.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.home.HomeHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the photo of an item comes from. */
enum class PhotoSource {
    /** The camera app: a new photo. */
    Camera,

    /** The system photo picker. */
    Photos,
}

/** Starts the camera or the photo picker. */
@Stable
class PhotoPickers internal constructor(private val camera: () -> Unit, private val photos: () -> Unit) {
    fun launch(source: PhotoSource) = when (source) {
        PhotoSource.Camera -> camera()
        PhotoSource.Photos -> photos()
    }
}

/**
 * The camera and the photo picker for one photo. The photo is read and scaled off the main thread, as Documents does
 * ([HomeHost.readPhoto]), then handed to [onPicked]; null when it could not be read. Nothing comes when the person
 * cancels. [onPreparing] says that reading has begun.
 */
@Composable
fun rememberPhotoPickers(host: HomeHost, onPreparing: () -> Unit = {}, onPicked: (UploadFile?) -> Unit): PhotoPickers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The camera writes here; kept when the phone rotates while the camera app is open.
    var cameraTarget by rememberSaveable { mutableStateOf<String?>(null) }

    fun take(uri: Uri, name: String) {
        onPreparing()
        scope.launch {
            val photo = withContext(Dispatchers.IO) { host.readPhoto(context, uri, name) }
            onPicked(photo)
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val target = cameraTarget?.let(Uri::parse)
        cameraTarget = null
        if (taken && target != null) take(target, "photo.jpg")
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) take(uri, "photo.jpg")
    }
    return PhotoPickers(
        camera = {
            val (_, uri) = newCameraFile(context)
            cameraTarget = uri.toString()
            runCatching { camera.launch(uri) }.onFailure {
                cameraTarget = null
                // No camera app: the photo picker instead.
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        },
        photos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}
