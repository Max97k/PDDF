package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.DecryptStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AutoUnlockUseCase(
    private val decryptPdfUseCase: DecryptPdfUseCase,
    private val passwordVaultUseCase: PasswordVaultUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    sealed interface AutoUnlockResult {
        data object NotEncrypted : AutoUnlockResult
        data class UnlockedWithSavedPassword(val outputUri: Uri, val matchedPasswordName: String) : AutoUnlockResult
        data object RequireManualPassword : AutoUnlockResult
        data class Error(val message: String) : AutoUnlockResult
    }

    /**
     * Returns a content:// URI for [file] via FileProvider so it can be consumed by
     * PdfViewerFragment and shared across process boundaries.
     * file:// URIs are rejected by PdfViewerFragment and trigger FileUriExposedException on
     * Android 7+.
     *
     * Falls back to Uri.fromFile() when FileProvider is unavailable (e.g. Robolectric unit tests),
     * so the fallback keeps tests green while production always gets a proper content:// URI.
     */
    private fun fileProviderUri(context: Context, file: File): Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        Uri.fromFile(file)
    }

    suspend fun tryAutoUnlock(
        context: Context,
        uri: Uri
    ): AutoUnlockResult = withContext(ioDispatcher) {
        // 1. Check if unencrypted
        var isEncrypted = true
        try {
            decryptPdfUseCase.openSafeInputStream(context, uri)?.use { input ->
                try {
                    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(
                        input.buffered(),
                        com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                    )
                    doc.use {
                        isEncrypted = it.isEncrypted
                    }
                } catch (_: Exception) {
                    isEncrypted = true
                }
            }
        } catch (_: Exception) {}

        if (!isEncrypted) {
            return@withContext AutoUnlockResult.NotEncrypted
        }

        // 2. Iterate through saved passwords
        val savedPasswords = passwordVaultUseCase.getAllDecryptedPasswords()
        for (saved in savedPasswords) {
            var tempFile: File? = null
            try {
                tempFile = File(context.cacheDir, "auto_decrypted_${System.currentTimeMillis()}.pdf")
                val status = decryptPdfUseCase.decrypt(
                    context = context,
                    inputUri = uri,
                    outputUri = Uri.fromFile(tempFile), // write via File path is fine for local I/O
                    passwordValue = saved.passwordValue
                )
                if (status == DecryptStatus.SUCCESS) {
                    return@withContext AutoUnlockResult.UnlockedWithSavedPassword(
                        outputUri = fileProviderUri(context, tempFile), // content:// required by PdfViewerFragment
                        matchedPasswordName = saved.name
                    )
                } else {
                    com.example.util.FileUtils.secureDelete(tempFile)
                }
            } catch (_: Exception) {
                com.example.util.FileUtils.secureDelete(tempFile)
            }
        }

        AutoUnlockResult.RequireManualPassword
    }

    suspend fun unlockWithManualPassword(
        context: Context,
        uri: Uri,
        enteredPassword: String,
        rememberPassword: Boolean,
        fileName: String
    ): Pair<DecryptStatus, Uri?> = withContext(ioDispatcher) {
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "auto_decrypted_${System.currentTimeMillis()}.pdf")
            val status = decryptPdfUseCase.decrypt(
                context = context,
                inputUri = uri,
                outputUri = Uri.fromFile(tempFile), // write via File path is fine for local I/O
                passwordValue = enteredPassword
            )
            if (status == DecryptStatus.SUCCESS) {
                if (rememberPassword && enteredPassword.isNotBlank()) {
                    passwordVaultUseCase.insertPassword(
                        name = fileName.ifBlank { "PDF Password" },
                        passwordValue = enteredPassword
                    )
                }
                return@withContext Pair(status, fileProviderUri(context, tempFile)) // content:// required by PdfViewerFragment
            } else {
                com.example.util.FileUtils.secureDelete(tempFile)
                return@withContext Pair(status, null)
            }
        } catch (_: Exception) {
            com.example.util.FileUtils.secureDelete(tempFile)
            return@withContext Pair(DecryptStatus.ERROR, null)
        }
    }
}
