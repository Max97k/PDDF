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

    suspend fun reEncrypt(
        sourceUri: String,
        destUri: String,
        newPasswordChars: CharArray,
        keyLength: Int = 256
    ): Result<Unit>

    suspend fun extractMetadata(
        sourceUri: String,
        passwordValue: String = ""
    ): PdfMetadata?

    suspend fun copy(
        sourceUri: String,
        destUri: String
    ): Boolean

    suspend fun createTempFile(
        prefix: String,
        suffix: String = ".pdf"
    ): String

    suspend fun getShareableUri(fileUri: String): String

    suspend fun deleteFile(filePathOrUri: String): Boolean

    suspend fun getFileName(uriString: String): String

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

    fun openSafeOutputStream(uriString: String): OutputStream?
}
