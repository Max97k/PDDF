package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class AutoUnlockUseCase(
    private val pdfRepository: PdfRepository,
    private val decryptPdfUseCase: DecryptPdfUseCase,
    private val passwordVaultUseCase: PasswordVaultUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    sealed interface AutoUnlockResult {
        data object NotEncrypted : AutoUnlockResult
        data class UnlockedWithSavedPassword(val outputUri: String, val matchedPasswordName: String) : AutoUnlockResult
        data object RequireManualPassword : AutoUnlockResult
        data class Error(val message: String) : AutoUnlockResult
    }

    open suspend fun tryAutoUnlock(
        sourceUriString: String
    ): AutoUnlockResult = withContext(ioDispatcher) {
        // 1. Check if unencrypted or corrupted / unsupported
        when (val check = pdfRepository.checkEncryption(sourceUriString)) {
            is PdfEncryptionCheck.Error -> return@withContext AutoUnlockResult.Error(check.message)
            is PdfEncryptionCheck.NotEncrypted -> return@withContext AutoUnlockResult.NotEncrypted
            is PdfEncryptionCheck.Encrypted -> { /* Continue to password trials */ }
        }

        // 2. Iterate through saved passwords
        val savedPasswords = passwordVaultUseCase.getAllDecryptedPasswords()
        for (saved in savedPasswords) {
            var tempUri: String? = null
            try {
                tempUri = pdfRepository.createTempFile("auto_decrypted_", ".pdf")
                val status = saved.usePasswordChars { chars ->
                    decryptPdfUseCase.decrypt(
                        inputUriString = sourceUriString,
                        outputUriString = tempUri,
                        passwordChars = chars
                    )
                }
                if (status == DecryptStatus.SUCCESS) {
                    passwordVaultUseCase.recordPasswordHit(saved.id)
                    val shareableUri = pdfRepository.getShareableUri(tempUri)
                    return@withContext AutoUnlockResult.UnlockedWithSavedPassword(
                        outputUri = shareableUri,
                        matchedPasswordName = saved.name
                    )
                } else if (status == DecryptStatus.UNSUPPORTED_ENCRYPTION) {
                    pdfRepository.deleteFile(tempUri)
                    return@withContext AutoUnlockResult.Error("Unsupported encryption")
                } else {
                    pdfRepository.deleteFile(tempUri)
                }
            } catch (_: Exception) {
                if (tempUri != null) {
                    pdfRepository.deleteFile(tempUri)
                }
            }
        }

        AutoUnlockResult.RequireManualPassword
    }

    open suspend fun unlockWithManualPassword(
        sourceUriString: String,
        enteredPassword: String,
        rememberPassword: Boolean,
        fileName: String
    ): Pair<DecryptStatus, String?> = withContext(ioDispatcher) {
        var tempUri: String? = null
        try {
            tempUri = pdfRepository.createTempFile("auto_decrypted_", ".pdf")
            val status = decryptPdfUseCase.decrypt(
                inputUriString = sourceUriString,
                outputUriString = tempUri,
                passwordValue = enteredPassword
            )
            if (status == DecryptStatus.SUCCESS) {
                if (rememberPassword && enteredPassword.isNotBlank()) {
                    passwordVaultUseCase.insertPassword(
                        name = fileName.ifBlank { "PDF Password" },
                        passwordValue = enteredPassword
                    )
                }
                val shareableUri = pdfRepository.getShareableUri(tempUri)
                return@withContext Pair(status, shareableUri)
            } else {
                pdfRepository.deleteFile(tempUri)
                return@withContext Pair(status, null)
            }
        } catch (_: Exception) {
            if (tempUri != null) {
                pdfRepository.deleteFile(tempUri)
            }
            return@withContext Pair(DecryptStatus.ERROR, null)
        }
    }
}
