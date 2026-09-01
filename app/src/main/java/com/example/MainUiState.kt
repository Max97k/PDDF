package com.example

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.data.PasswordEntity
import com.example.data.ThemeMode

/**
 * State representation for batch decryption progress.
 */
@Immutable
data class BatchState(
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0
) {
    val progressFraction: Float
        get() = if (total > 0) progress.toFloat() / total else 0f
}

/**
 * Comprehensive single immutable UI state for the PDF Decryptor screen.
 * Marked with @Immutable to enforce Compose compiler stability and skipping.
 */
@Immutable
data class MainUiState(
    // 1. File Selection State
    val selectedUris: List<Uri> = emptyList(),
    val selectedFileNames: List<String> = emptyList(),
    val selectedMetadata: PdfMetadata? = null,

    // 2. Password Input State
    val password: String = "",
    val passwordVisible: Boolean = false,

    // 3. Decryption & Processing Status
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val lastDecryptedUri: Uri? = null,
    val previewPdfUri: Uri? = null,

    // 4. Batch Processing State
    val batchState: BatchState = BatchState(),

    // 5. External Intent & Auto-Unlock State
    val isAutoUnlocking: Boolean = false,
    val showAutoUnlockPasswordPrompt: Boolean = false,
    val autoUnlockTargetUri: Uri? = null,
    val autoUnlockFileName: String = "",
    val autoUnlockErrorMessage: String? = null,

    // 6. Dialog Visibility Flags
    val showSavePasswordDialog: Boolean = false,
    val showPasswordListDialog: Boolean = false,
    val showWhatsNewDialog: Boolean = false,

    // 7. User Preferences & Settings
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val conflictMode: ConflictMode = ConflictMode.SAVE_AS_COPY,
    val rememberConflictChoice: Boolean = false,

    // 8. Password Vault Data
    val savedPasswords: List<PasswordEntity> = emptyList()
) {
    // Computed Properties for Clean Leaf Composable Consumption
    val hasSelectedFiles: Boolean
        get() = selectedUris.isNotEmpty()

    val fileCount: Int
        get() = selectedUris.size

    val isSingleFile: Boolean
        get() = selectedUris.size == 1

    val isBatch: Boolean
        get() = selectedUris.size > 1

    val canDecrypt: Boolean
        get() = hasSelectedFiles && password.isNotBlank() && !isProcessing && !batchState.isProcessing

    val isSecureModeActive: Boolean
        get() = showPasswordListDialog || showSavePasswordDialog || showAutoUnlockPasswordPrompt

    val activePreviewUri: Uri?
        get() = previewPdfUri ?: lastDecryptedUri ?: if (selectedMetadata?.isEncrypted == false) selectedUris.firstOrNull() else null
}
