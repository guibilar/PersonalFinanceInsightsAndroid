package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.ImportResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The API's upload limit. */
const val MAX_STATEMENT_BYTES = 5 * 1024 * 1024

/** Uploads a statement file into an account; [confirmed] imports it even though it looks like another account's. */
suspend fun FinanceApi.importStatement(fileName: String, bytes: ByteArray, accountId: String, confirmed: Boolean = false): ImportResult {
    val text = "text/plain".toMediaType()
    val file = MultipartBody.Part.createFormData("file", fileName, bytes.toRequestBody("text/csv".toMediaType()))
    return importStatement(file, accountId.toRequestBody(text), if (confirmed) "true".toRequestBody(text) else null)
}

/** The API's reason to think a statement is another account's (409, code "account-mismatch"); null for anything else. */
val ApiError.accountMismatch: String?
    get() = if (this is ApiError.Http && status == 409 && code == "account-mismatch") {
        detail ?: "This file looks like it belongs to another account."
    } else null

/** What to say when an import failed, as the web app does. */
fun ApiError.importMessage(): String = when {
    this is ApiError.Unreachable -> message!!
    this is ApiError.Http && status == 413 -> "The file is too large to upload (the limit is 5 MB)."
    this is ApiError.Http && status == 404 -> "That account no longer exists. Choose another one."
    this is ApiError.Http && status == 400 -> validationOr("The statement could not be read.")
    else -> "The import failed. Please try again."
}
