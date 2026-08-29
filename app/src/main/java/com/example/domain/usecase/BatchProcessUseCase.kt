package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.ConflictMode
import com.example.DecryptStatus
import com.example.util.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class BatchProcessUseCase(
    private val decryptPdfUseCase: DecryptPdfUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    data class BatchResult(
        val successCount: Int = 0,
        val notEncryptedCount: Int = 0,
        val wrongPasswordCount: Int = 0,
        val unsupportedCount: Int = 0,
        val errorCount: Int = 0,
        val cancelledCount: Int = 0,
        val lastDecryptedUri: Uri? = null,
        val errorOutputDir: Boolean = false
    )

    suspend fun processInPlace(
        context: Context,
        inputUris: List<Uri>,
        passwordValue: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchResult = withContext(ioDispatcher) {
        var successCount = 0
        var notEncryptedCount = 0
        var wrongPasswordCount = 0
        var unsupportedCount = 0
        var errorCount = 0
        var cancelledCount = 0
        var lastUri: Uri? = null

        try {
            for ((index, inputUri) in inputUris.withIndex()) {
                ensureActive()
                var tempFile: File? = null
                try {
                    tempFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}_$index.pdf")
                    val status = decryptPdfUseCase.decrypt(
                        context = context,
                        inputUri = inputUri,
                        outputUri = Uri.fromFile(tempFile),
                        passwordValue = passwordValue
                    )

                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            val destUri = inputUri
                            decryptPdfUseCase.openSafeInputStream(context, Uri.fromFile(tempFile))?.use { input ->
                                decryptPdfUseCase.openSafeOutputStream(context, destUri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            lastUri = destUri
                            successCount++
                        }
                        DecryptStatus.NOT_ENCRYPTED -> notEncryptedCount++
                        DecryptStatus.WRONG_PASSWORD -> wrongPasswordCount++
                        DecryptStatus.UNSUPPORTED_ENCRYPTION -> unsupportedCount++
                        DecryptStatus.ERROR -> errorCount++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorCount++
                } finally {
                    FileUtils.secureDelete(tempFile)
                }
                onProgress(index + 1, inputUris.size)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelledCount = inputUris.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
        }

        BatchResult(
            successCount = successCount,
            notEncryptedCount = notEncryptedCount,
            wrongPasswordCount = wrongPasswordCount,
            unsupportedCount = unsupportedCount,
            errorCount = errorCount,
            cancelledCount = cancelledCount,
            lastDecryptedUri = lastUri
        )
    }

    suspend fun processToDirectory(
        context: Context,
        inputUris: List<Uri>,
        outputDirectoryUri: Uri,
        passwordValue: String,
        conflictMode: ConflictMode,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchResult = withContext(ioDispatcher) {
        val documentTree = DocumentFile.fromTreeUri(context, outputDirectoryUri)
            ?: return@withContext BatchResult(errorOutputDir = true)

        var successCount = 0
        var notEncryptedCount = 0
        var wrongPasswordCount = 0
        var unsupportedCount = 0
        var errorCount = 0
        var cancelledCount = 0
        var lastUri: Uri? = null

        try {
            for ((index, inputUri) in inputUris.withIndex()) {
                ensureActive()
                var tempFile: File? = null
                try {
                    tempFile = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}_$index.pdf")
                    val status = decryptPdfUseCase.decrypt(
                        context = context,
                        inputUri = inputUri,
                        outputUri = Uri.fromFile(tempFile),
                        passwordValue = passwordValue
                    )

                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            val originalName = FileUtils.getFileName(context, inputUri)
                            val targetFileName = if (conflictMode == ConflictMode.OVERWRITE) {
                                originalName
                            } else {
                                getUniqueFileName(documentTree, originalName)
                            }

                            if (conflictMode == ConflictMode.OVERWRITE) {
                                val existingFile = documentTree.findFile(originalName)
                                existingFile?.delete()
                            }

                            val destDoc = documentTree.createFile("application/pdf", targetFileName)
                            if (destDoc != null) {
                                val destUri = destDoc.uri
                                decryptPdfUseCase.openSafeInputStream(context, Uri.fromFile(tempFile))?.use { input ->
                                    decryptPdfUseCase.openSafeOutputStream(context, destUri)?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                lastUri = destUri
                                successCount++
                            } else {
                                errorCount++
                            }
                        }
                        DecryptStatus.NOT_ENCRYPTED -> notEncryptedCount++
                        DecryptStatus.WRONG_PASSWORD -> wrongPasswordCount++
                        DecryptStatus.UNSUPPORTED_ENCRYPTION -> unsupportedCount++
                        DecryptStatus.ERROR -> errorCount++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorCount++
                } finally {
                    FileUtils.secureDelete(tempFile)
                }
                onProgress(index + 1, inputUris.size)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelledCount = inputUris.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
        }

        BatchResult(
            successCount = successCount,
            notEncryptedCount = notEncryptedCount,
            wrongPasswordCount = wrongPasswordCount,
            unsupportedCount = unsupportedCount,
            errorCount = errorCount,
            cancelledCount = cancelledCount,
            lastDecryptedUri = lastUri
        )
    }

    private fun getUniqueFileName(documentTree: DocumentFile, targetFileName: String): String {
        if (documentTree.findFile(targetFileName) == null) {
            return targetFileName
        }
        val baseName = if (targetFileName.contains(".")) {
            targetFileName.substringBeforeLast(".")
        } else {
            targetFileName
        }
        val ext = if (targetFileName.contains(".")) {
            "." + targetFileName.substringAfterLast(".")
        } else {
            ""
        }

        var index = 1
        while (true) {
            val candidate = "$baseName ($index)$ext"
            if (documentTree.findFile(candidate) == null) {
                return candidate
            }
            index++
        }
    }

    private fun deleteOriginalFile(context: Context, uri: Uri): Boolean {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
