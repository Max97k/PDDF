package com.max97k.pddf.domain.usecase

import com.max97k.pddf.ConflictMode
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.domain.repository.PasswordRepository
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.util.MemoryUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class BatchProcessUseCase(
    private val pdfRepository: PdfRepository,
    private val decryptPdfUseCase: DecryptPdfUseCase,
    private val passwordRepository: PasswordRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    data class BatchResult(
        val successCount: Int = 0,
        val notEncryptedCount: Int = 0,
        val wrongPasswordCount: Int = 0,
        val unsupportedCount: Int = 0,
        val errorCount: Int = 0,
        val cancelledCount: Int = 0,
        val lastDecryptedUri: String? = null,
        val errorOutputDir: Boolean = false
    )

    private suspend fun tryDecrypt(
        inputUriString: String,
        outputUriString: String,
        manualPasswordValue: String,
        vaultPasswords: List<PasswordEntity>
    ): DecryptStatus {
        val allPasswordChars = mutableListOf<CharArray>()

        allPasswordChars.add(manualPasswordValue.toCharArray())

        for (password in vaultPasswords) {
            allPasswordChars.add(password.getPasswordChars())
        }

        var status = DecryptStatus.WRONG_PASSWORD
        try {
            for (chars in allPasswordChars) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val currentStatus = decryptPdfUseCase.decrypt(
                    inputUriString = inputUriString,
                    outputUriString = outputUriString,
                    passwordChars = chars
                )

                if (currentStatus != DecryptStatus.WRONG_PASSWORD) {
                    status = currentStatus
                    break
                }
            }
        } finally {
            for (chars in allPasswordChars) {
                MemoryUtils.wipe(chars)
            }
        }

        return status
    }

    suspend fun processInPlace(
        inputUriStrings: List<String>,
        passwordValue: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchResult = withContext(ioDispatcher) {
        var successCount = 0
        var notEncryptedCount = 0
        var wrongPasswordCount = 0
        var unsupportedCount = 0
        var errorCount = 0
        var cancelledCount = 0
        var lastUri: String? = null

        val vaultPasswords = passwordRepository.getAllDecryptedPasswords()

        try {
            for ((index, inputUri) in inputUriStrings.withIndex()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                var tempUri: String? = null
                try {
                    tempUri = pdfRepository.createTempFile("decrypted_${System.currentTimeMillis()}_$index", ".pdf")
                    val status = tryDecrypt(inputUri, tempUri, passwordValue, vaultPasswords)

                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            pdfRepository.copy(tempUri, inputUri)
                            lastUri = inputUri
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
                    if (tempUri != null) {
                        pdfRepository.deleteFile(tempUri)
                    }
                }
                onProgress(index + 1, inputUriStrings.size)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelledCount = inputUriStrings.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
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
        inputUriStrings: List<String>,
        outputDirectoryUriString: String,
        passwordValue: String,
        conflictMode: ConflictMode,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchResult = withContext(ioDispatcher) {
        var successCount = 0
        var notEncryptedCount = 0
        var wrongPasswordCount = 0
        var unsupportedCount = 0
        var errorCount = 0
        var cancelledCount = 0
        var lastUri: String? = null

        val vaultPasswords = passwordRepository.getAllDecryptedPasswords()

        try {
            for ((index, inputUri) in inputUriStrings.withIndex()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                var tempUri: String? = null
                try {
                    tempUri = pdfRepository.createTempFile("decrypted_${System.currentTimeMillis()}_$index", ".pdf")
                    val status = tryDecrypt(inputUri, tempUri, passwordValue, vaultPasswords)

                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            val originalName = pdfRepository.getFileName(inputUri)
                            val targetFileName = if (conflictMode == ConflictMode.OVERWRITE) {
                                originalName
                            } else {
                                getUniqueFileName(outputDirectoryUriString, originalName)
                            }

                            if (conflictMode == ConflictMode.OVERWRITE) {
                                pdfRepository.deleteDocumentInDirectory(outputDirectoryUriString, originalName)
                            }

                            val destDocUri = pdfRepository.createDocumentInDirectory(
                                directoryUriString = outputDirectoryUriString,
                                displayName = targetFileName,
                                mimeType = "application/pdf"
                            )
                            if (destDocUri != null) {
                                pdfRepository.copy(tempUri, destDocUri)
                                lastUri = destDocUri
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
                    if (tempUri != null) {
                        pdfRepository.deleteFile(tempUri)
                    }
                }
                onProgress(index + 1, inputUriStrings.size)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelledCount = inputUriStrings.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
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

    private suspend fun getUniqueFileName(directoryUriString: String, originalName: String): String {
        val baseName = originalName.substringBeforeLast(".")
        val extension = if (originalName.contains(".")) ".${originalName.substringAfterLast(".")}" else ""
        var candidateName = originalName
        var counter = 1

        while (pdfRepository.findDocumentInDirectory(directoryUriString, candidateName) != null) {
            candidateName = "$baseName ($counter)$extension"
            counter++
        }
        return candidateName
    }
}
