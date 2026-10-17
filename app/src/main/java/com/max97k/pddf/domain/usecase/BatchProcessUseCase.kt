package com.max97k.pddf.domain.usecase

import com.max97k.pddf.ConflictMode
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class BatchProcessUseCase(
    private val pdfRepository: PdfRepository,
    private val decryptPdfUseCase: DecryptPdfUseCase,
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
        val errorOutputDir: Boolean = false,
        val failedFiles: List<com.max97k.pddf.domain.model.FailedFileRecord> = emptyList()
    )

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
        val failedRecords = mutableListOf<com.max97k.pddf.domain.model.FailedFileRecord>()

        try {
            for ((index, inputUri) in inputUriStrings.withIndex()) {
                ensureActive()
                var tempUri: String? = null
                try {
                    tempUri = pdfRepository.createTempFile("decrypted_${System.currentTimeMillis()}_$index", ".pdf")
                    val status = decryptPdfUseCase.decrypt(
                        inputUriString = inputUri,
                        outputUriString = tempUri,
                        passwordValue = passwordValue
                    )

                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            pdfRepository.copy(tempUri, inputUri)
                            lastUri = inputUri
                            successCount++
                        }
                        DecryptStatus.NOT_ENCRYPTED -> {
                            notEncryptedCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.NOT_ENCRYPTED, "Not encrypted"))
                        }
                        DecryptStatus.WRONG_PASSWORD -> {
                            wrongPasswordCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.WRONG_PASSWORD, "Wrong password"))
                        }
                        DecryptStatus.UNSUPPORTED_ENCRYPTION -> {
                            unsupportedCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.UNSUPPORTED_ENCRYPTION, "Unsupported encryption"))
                        }
                        DecryptStatus.ERROR -> {
                            errorCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.ERROR, "Error decrypting"))
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorCount++
                    failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.ERROR, e.message ?: "Unknown error"))
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
            lastDecryptedUri = lastUri,
            failedFiles = failedRecords
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
        val failedRecords = mutableListOf<com.max97k.pddf.domain.model.FailedFileRecord>()

        try {
            for ((index, inputUri) in inputUriStrings.withIndex()) {
                ensureActive()
                var tempUri: String? = null
                try {
                    tempUri = pdfRepository.createTempFile("decrypted_${System.currentTimeMillis()}_$index", ".pdf")
                    val status = decryptPdfUseCase.decrypt(
                        inputUriString = inputUri,
                        outputUriString = tempUri,
                        passwordValue = passwordValue
                    )

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
                                failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, originalName, DecryptStatus.ERROR, "Failed to copy to output directory"))
                            }
                        }
                        DecryptStatus.NOT_ENCRYPTED -> {
                            notEncryptedCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.NOT_ENCRYPTED, "Not encrypted"))
                        }
                        DecryptStatus.WRONG_PASSWORD -> {
                            wrongPasswordCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.WRONG_PASSWORD, "Wrong password"))
                        }
                        DecryptStatus.UNSUPPORTED_ENCRYPTION -> {
                            unsupportedCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.UNSUPPORTED_ENCRYPTION, "Unsupported encryption"))
                        }
                        DecryptStatus.ERROR -> {
                            errorCount++
                            failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.ERROR, "Error decrypting"))
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorCount++
                    failedRecords.add(com.max97k.pddf.domain.model.FailedFileRecord(inputUri, pdfRepository.getFileName(inputUri), DecryptStatus.ERROR, e.message ?: "Unknown error"))
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
            lastDecryptedUri = lastUri,
            failedFiles = failedRecords
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
