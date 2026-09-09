package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.DecryptStatus
import com.example.PdfMetadata
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

open class DecryptPdfUseCase(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    open suspend fun decrypt(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        passwordValue: String
    ): DecryptStatus = withContext(ioDispatcher) {
        // 1. Check if unencrypted
        try {
            openSafeInputStream(context, inputUri)?.use { inputStream ->
                val docWithoutPass = try {
                    PDDocument.load(inputStream.buffered(), com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))
                } catch (_: Exception) {
                    null
                }
                docWithoutPass?.use {
                    if (!it.isEncrypted) {
                        // Copy the original bytes to outputUri so the caller gets a valid file.
                        val copySucceeded = try {
                            val srcStream = openSafeInputStream(context, inputUri)
                            val dstStream = openSafeOutputStream(context, outputUri)
                            if (srcStream != null && dstStream != null) {
                                srcStream.use { s ->
                                    dstStream.buffered().use { d ->
                                        s.copyTo(d)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        } catch (_: Exception) {
                            false
                        }
                        return@withContext if (copySucceeded) {
                            DecryptStatus.NOT_ENCRYPTED
                        } else {
                            DecryptStatus.ERROR
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore pre-check exception
        }

        // 2. Attempt decryption with password
        try {
            openSafeInputStream(context, inputUri)?.use { inputStream ->
                PDDocument.load(
                    inputStream.buffered(),
                    passwordValue,
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                ).use { document ->
                    if (!document.isEncrypted) {
                        return@withContext DecryptStatus.NOT_ENCRYPTED
                    }
                    document.setAllSecurityToBeRemoved(true)
                    val outStream = openSafeOutputStream(context, outputUri)
                        ?: return@withContext DecryptStatus.ERROR
                    outStream.buffered().use { outputStream ->
                        document.save(outputStream)
                        return@withContext DecryptStatus.SUCCESS
                    }
                }
            }
        } catch (_: InvalidPasswordException) {
            return@withContext DecryptStatus.WRONG_PASSWORD
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            return@withContext when {
                msg.contains("password") || msg.contains("incorrect password") || msg.contains("password is required") ->
                    DecryptStatus.WRONG_PASSWORD
                msg.contains("security handler") ||
                msg.contains("cryptfilter") ||
                msg.contains("certificate") ||
                msg.contains("public key") ||
                msg.contains("unsupported") ||
                msg.contains("filter") ||
                msg.contains("drm") ||
                msg.contains("algorithm") ->
                    DecryptStatus.UNSUPPORTED_ENCRYPTION
                else ->
                    DecryptStatus.ERROR
            }
        }
        DecryptStatus.ERROR
    }

    suspend fun extractMetadata(context: Context, uri: Uri, passwordValue: String = ""): PdfMetadata? = withContext(ioDispatcher) {
        var sizeMb = 0.0
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                sizeMb = fd.statSize.toDouble() / (1024 * 1024)
            }
        } catch (_: Exception) {}

        try {
            openSafeInputStream(context, uri)?.use { inputStream ->
                val doc = try {
                    PDDocument.load(
                        inputStream.buffered(),
                        passwordValue,
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                } catch (_: Exception) {
                    null
                }
                doc?.use {
                    val info = it.documentInformation
                    val title = info?.title ?: "Unknown"
                    val author = info?.author ?: "Unknown"
                    val pages = it.numberOfPages
                    val enc = it.encryption
                    val encMethod = if (enc != null) "${enc.filter} ${enc.length}-bit" else if (it.isEncrypted) "Standard Encrypted" else "None"
                    val perm = it.currentAccessPermission
                    val canPrint = perm?.canPrint() ?: true
                    val canCopy = perm?.canExtractContent() ?: true
                    return@withContext PdfMetadata(
                        title = title,
                        author = author,
                        pageCount = pages,
                        fileSizeMb = sizeMb,
                        encryptionMethod = encMethod,
                        canPrint = canPrint,
                        canCopy = canCopy
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    open fun openSafeInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    FileInputStream(File(uri.path!!))
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    open fun openSafeOutputStream(context: Context, uri: Uri): OutputStream? {
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                FileOutputStream(File(uri.path!!))
            } else {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")
                } catch (_: Exception) {
                    context.contentResolver.openOutputStream(uri)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
