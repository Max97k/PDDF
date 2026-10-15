package com.max97k.pddf.domain.repository

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import java.io.InputStream
import java.io.OutputStream

sealed interface PdfEncryptionCheck {
    data object NotEncrypted : PdfEncryptionCheck
    data object Encrypted : PdfEncryptionCheck
    data class Error(val message: String) : PdfEncryptionCheck
}

interface PdfRepository {
    suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck

    suspend fun decrypt(
        sourceUri: String,
        destUri: String,
        passwordChars: CharArray
    ): DecryptStatus

    suspend fun decrypt(
        sourceUri: String,
        destUri: String,
        passwordValue: String
    ): DecryptStatus


    suspend fun exportPageAsImage(
        sourceUri: String,
        pageIndex: Int,
        outputUri: String,
        format: android.graphics.Bitmap.CompressFormat,
        quality: Int
    ): Boolean

    suspend fun extractMetadata(
        sourceUri: String,
        passwordValue: String = ""
    ): PdfMetadata?

    suspend fun compressPdf(
        sourceUri: String,
        destUri: String
    ): com.max97k.pddf.util.Result<Long>

    suspend fun copy(
        sourceUri: String,
        destUri: String
    ): Boolean

    suspend fun createTempFile(
        prefix: String,
        suffix: String = ".pdf"
    ): String

    suspend fun addWatermark(
        sourceUri: String,
        destUri: String,
        text: String,
        opacity: Float = 0.3f,
        fontSize: Float = 48f
    ): Boolean

    suspend fun getShareableUri(fileUri: String): String

    suspend fun deleteFile(filePathOrUri: String): Boolean

    suspend fun getFileName(uriString: String): String

    suspend fun extractPages(sourceUri: String, destUri: String, pageRange: String): kotlin.Result<Int>

    suspend fun createDocumentInDirectory(
        directoryUriString: String,
        displayName: String,
        mimeType: String = "application/pdf"
    ): String?

    suspend fun findDocumentInDirectory(
        directoryUriString: String,
        displayName: String
    ): String?

    suspend fun deleteDocumentInDirectory(
        directoryUriString: String,
        displayName: String
    ): Boolean

    fun openSafeInputStream(uriString: String): InputStream?

    suspend fun packageToZip(fileUris: List<String>, outputZipUri: String, onProgress: (Int, Int) -> Unit): Boolean

    fun openSafeOutputStream(uriString: String): OutputStream?

    suspend fun rotatePages(
        sourceUri: String,
        destUri: String,
        pageIndices: List<Int>?,
        rotationDegrees: Int
    ): Boolean
}
