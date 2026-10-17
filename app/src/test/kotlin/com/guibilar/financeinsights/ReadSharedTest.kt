package com.guibilar.financeinsights

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** T101: a file shared to Hestia is read whole up to a document file's limit, not cut at a statement's 5 MB. */
@RunWith(RobolectricTestRunner::class)
class ReadSharedTest {
    @get:Rule val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a shared PDF of 8 MB is read whole`() {
        val bytes = "%PDF-1.7\n".toByteArray() + ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val file = folder.newFile("scan.pdf").apply { writeBytes(bytes) }

        val read = readShared(context, Uri.fromFile(file))

        assertArrayEquals(bytes, read?.bytes)
    }
}
