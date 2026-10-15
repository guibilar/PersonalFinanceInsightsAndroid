package com.guibilar.financeinsights.documents

import android.graphics.Bitmap
import android.graphics.Color
import com.guibilar.financeinsights.FakeDocumentsApi
import com.guibilar.financeinsights.documents.ui.DocumentThumbnails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** T61: the thumbnails come from the server once, and a file without one is not asked for again. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentThumbnailsTest {
    private val api = FakeDocumentsApi()
    private val document = api.documents.first()
    private val file = document.files.first()

    private fun calls() = api.sent.count { it.first == "thumbnail" }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun `a thumbnail loads once, then comes from memory`() = runTest {
        api.thumbnails[file.id] = jpeg(360, 480)
        val thumbnails = DocumentThumbnails(api)

        val image = thumbnails.load(document.id, file.id)

        assertNotNull(image)
        assertEquals(360 to 480, image!!.width to image.height)
        thumbnails.load(document.id, file.id)
        assertEquals(1, calls())
    }

    @Test
    fun `a file without a thumbnail (404) is not asked for again`() = runTest {
        val thumbnails = DocumentThumbnails(api)

        assertNull(thumbnails.load(document.id, file.id))
        assertNull(thumbnails.load(document.id, file.id))
        assertEquals(1, calls())
    }

    @Test
    fun `without a network, the next look tries again`() = runTest {
        api.thumbnails[file.id] = jpeg(40, 40)
        api.failing += "thumbnail"
        val thumbnails = DocumentThumbnails(api)

        assertNull(thumbnails.load(document.id, file.id))
        api.failing -= "thumbnail"
        assertNotNull(thumbnails.load(document.id, file.id))
        assertEquals(2, calls())
    }

    @Test
    fun `bytes that are not an image give the icon`() = runTest {
        api.thumbnails[file.id] = "not a picture".toByteArray()

        assertNull(DocumentThumbnails(api).load(document.id, file.id))
    }
}
