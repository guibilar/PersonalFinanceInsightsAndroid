package com.guibilar.financeinsights.home

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.guibilar.financeinsights.core.documents.UploadFile

/**
 * What the Home pages need from the app and the other modules (D14): to open a document, a transaction or a task in
 * its module, and to read photos as the Documents module does (T12). The app implements it; this module never depends
 * on another feature module.
 */
interface HomeHost {
    /** Opens one document, over the Home page that is open. */
    fun openDocument(id: String)

    /** Opens one transaction in Money. */
    fun openTransaction(id: String)

    /** Opens one task (the Tasks screen with its sheet). */
    fun openTask(id: String)

    /**
     * A picked or new photo, read and made ready for the upload as Documents does it: upright, scaled, as JPEG. Null
     * when it can't be read. Runs off the main thread.
     */
    fun readPhoto(context: Context, uri: Uri, name: String): UploadFile?

    /** An image file for the screen: upright, at most [maxSide] pixels on its long side; null when it can't be decoded. */
    fun decodePhoto(bytes: ByteArray, maxSide: Int): Bitmap?
}
