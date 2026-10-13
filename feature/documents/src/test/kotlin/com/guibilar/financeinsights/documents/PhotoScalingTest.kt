package com.guibilar.financeinsights.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.guibilar.financeinsights.core.documents.FileType
import com.guibilar.financeinsights.core.documents.UploadFile
import com.guibilar.financeinsights.documents.ui.jpegName
import com.guibilar.financeinsights.documents.ui.preparePhoto
import com.guibilar.financeinsights.documents.ui.safeFileName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Photos are made small and upright before the upload; PDFs and small JPEGs go as they are. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoScalingTest {
    @get:Rule val folder = TemporaryFolder()

    private fun image(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = ByteArrayOutputStream()
        bitmap.compress(format, 90, out)
        return out.toByteArray()
    }

    /** The same JPEG, with an EXIF orientation (a phone held upright: the pixels lie on their side). */
    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val file = folder.newFile("photo.jpg").apply { writeBytes(jpeg) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file.readBytes()
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun `a large photo becomes a JPEG of 2500 pixels on its long side`() {
        val photo = UploadFile("IMG_0001.png", image(4000, 3000, Bitmap.CompressFormat.PNG))

        val prepared = preparePhoto(photo)

        assertEquals(FileType.Jpeg, prepared.type)
        assertEquals("IMG_0001.jpg", prepared.name)
        assertEquals(2500 to 1875, size(prepared.bytes))
    }

    @Test
    fun `a photo taken upright is turned upright`() {
        val photo = UploadFile("IMG_0002.jpg", withOrientation(image(3000, 2000), ExifInterface.ORIENTATION_ROTATE_90))

        val prepared = preparePhoto(photo)

        assertEquals(1667 to 2500, size(prepared.bytes))
    }

    @Test
    fun `a small upright JPEG and a PDF go as they are`() {
        val small = UploadFile("receipt.jpg", image(1200, 1600))
        assertSame(small, preparePhoto(small))

        val pdf = UploadFile("scan.pdf", "%PDF-1.7".toByteArray())
        assertSame(pdf, preparePhoto(pdf))
    }

    @Test
    fun `a small PNG becomes a JPEG of the same size`() {
        val png = UploadFile("screenshot.png", image(800, 600, Bitmap.CompressFormat.PNG))

        val prepared = preparePhoto(png)

        assertEquals(FileType.Jpeg, prepared.type)
        assertEquals(800 to 600, size(prepared.bytes))
    }

    @Test
    fun `an image that can't be decoded goes as it is`() {
        val broken = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val file = UploadFile("broken.jpg", broken)
        assertArrayEquals(broken, preparePhoto(file).bytes)
    }

    @Test
    fun `file names are safe on disk`() {
        assertEquals("IMG_1234.jpg", jpegName("IMG_1234.heic"))
        assertEquals("scan.jpg", jpegName("scan"))
        assertEquals("Lease_2026__page_1_.pdf", safeFileName("Lease 2026 (page 1).pdf"))
        assertEquals("_.._passwd", safeFileName("../../passwd"))
        assertEquals("file", safeFileName("..."))
    }
}
