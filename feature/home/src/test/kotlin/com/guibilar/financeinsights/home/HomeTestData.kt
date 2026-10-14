package com.guibilar.financeinsights.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.guibilar.financeinsights.DocumentsSamples
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.FakeTasksApi
import com.guibilar.financeinsights.HomeSamples
import com.guibilar.financeinsights.TasksSamples
import com.guibilar.financeinsights.core.documents.Document
import com.guibilar.financeinsights.core.documents.DocumentFile
import com.guibilar.financeinsights.core.documents.DocumentKind
import com.guibilar.financeinsights.core.documents.OcrStatus
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.core.tasks.RepeatMode
import com.guibilar.financeinsights.core.tasks.RepeatUnit
import com.guibilar.financeinsights.core.tasks.TaskItem
import com.guibilar.financeinsights.core.tasks.TaskLink
import com.guibilar.financeinsights.core.tasks.TaskLinkKinds
import com.guibilar.financeinsights.core.tasks.TaskRepeat

/** The data of the Home tests: the maintenance tasks of the sample items, and the photo of the dishwasher. */
object HomeTestData {
    private const val CREATED = "2026-09-20T10:00:00Z"

    /** "Clean the dishwasher filter", every month, linked to the dishwasher. */
    val filter = TaskItem(
        id = HomeSamples.FILTER_TASK,
        title = "Clean the dishwasher filter",
        dueOn = "2026-10-25",
        repeat = TaskRepeat(1, RepeatUnit.Months, RepeatMode.FromCompletion),
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(TaskLink("k20", TaskLinkKinds.HOME_ITEM, HomeSamples.dishwasher.id, CREATED)),
    )

    /** "Smoke detector batteries", every 12 months, linked to the smoke detectors. */
    val batteries = TaskItem(
        id = HomeSamples.BATTERY_TASK,
        title = "Smoke detector batteries",
        dueOn = "2026-10-09",
        repeat = TaskRepeat(12, RepeatUnit.Months, RepeatMode.FromDueDate),
        createdAt = CREATED,
        updatedAt = CREATED,
        links = listOf(TaskLink("k21", TaskLinkKinds.HOME_ITEM, HomeSamples.smokeDetectors.id, CREATED)),
    )

    /** The tasks of the samples, with the two maintenance tasks. */
    fun tasks() = FakeTasksApi(TasksSamples.all + filter + batteries)

    /** The photo of the dishwasher, a JPEG document. */
    val photo = Document(
        id = HomeSamples.PHOTO_DOCUMENT,
        title = "Dishwasher photo",
        kind = DocumentKind.Other,
        createdAt = CREATED,
        updatedAt = CREATED,
        ocrStatus = OcrStatus.Done,
        files = listOf(DocumentFile("f9", "dishwasher.jpg", "image/jpeg", 640_000, "e5f6", CREATED)),
    )

    /** The documents of the samples, with the photo. */
    fun documents() = FakeDocumentsApi(DocumentsSamples.all + photo)

    /** A picture for the photo: a soft gradient, as a stand-in for a real photo. */
    fun picture(width: Int = 600, height: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.rgb(176, 190, 197), Color.rgb(84, 110, 122), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

/** A host that records what opens, and gives a picture for each image. */
class FakeHomeHost(private val picture: () -> Bitmap? = { null }) : HomeHost {
    val opened = mutableListOf<String>()

    override fun openDocument(id: String) {
        opened += "document $id"
    }

    override fun openTransaction(id: String) {
        opened += "transaction $id"
    }

    override fun openTask(id: String) {
        opened += "task $id"
    }

    override fun readPhoto(context: Context, uri: Uri, name: String): UploadFile? = UploadFile(name, DocumentsSamples.jpegBytes)

    override fun decodePhoto(bytes: ByteArray, maxSide: Int): Bitmap? = picture()
}
