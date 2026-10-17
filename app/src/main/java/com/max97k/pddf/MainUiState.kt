package com.max97k.pddf

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.data.ThemeMode
import com.max97k.pddf.data.DensityMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * State representation for batch decryption progress.
 */
@Immutable
data class BatchState(
    val isProcessing: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val failedFiles: ImmutableList<com.max97k.pddf.domain.model.FailedFileRecord> = persistentListOf()
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
    val selectedUris: ImmutableList<Uri> = persistentListOf(),
    val selectedFileNames: ImmutableList<String> = persistentListOf(),
    val selectedMetadata: PdfMetadata? = null,

    // 2. Password Input State
    val password: String = "",
    val passwordVisible: Boolean = false,

    // 3. Decryption & Processing Status
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val lastDecryptedUri: Uri? = null,
    val lastExportSuccess: Boolean? = null,
    val previewPdfUri: Uri? = null,
    val lastDecryptionDurationMs: Long? = null,
    val exportFormat: String = "csv",

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
    val showStampDialog: Boolean = false,
    val showBatchFailureDialog: Boolean = false,
    val showReEncryptDialog: Boolean = false,
    val activeReEncryptUri: Uri? = null,

    // 7. User Preferences & Settings
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val densityMode: DensityMode = DensityMode.STANDARD,
    val conflictMode: ConflictMode = ConflictMode.SAVE_AS_COPY,
    val rememberConflictChoice: Boolean = false,
    val screenshotProtectionEnabled: Boolean = false,
    val batchNotificationPreference: com.max97k.pddf.data.NotificationPreference = com.max97k.pddf.data.NotificationPreference.SOUND,
    val autoLockDuration: Long = 60000L,

    // 8. Password Vault Data
    val savedPasswords: ImmutableList<PasswordEntity> = persistentListOf(),

    // 9. Google Play In-App Updates
    val appUpdateStatus: com.max97k.pddf.domain.model.AppUpdateStatus = com.max97k.pddf.domain.model.AppUpdateStatus.Idle
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

    // True when any sensitive dialog is open (drives temporary FLAG_SECURE).
    val isDialogSecureModeActive: Boolean
        get() = showPasswordListDialog || showSavePasswordDialog || showAutoUnlockPasswordPrompt || showReEncryptDialog

    // Combined: FLAG_SECURE should be active when user-controlled OR a sensitive dialog is open.
    val isSecureModeActive: Boolean
        get() = screenshotProtectionEnabled || isDialogSecureModeActive

    val activePreviewUri: Uri?
        get() = previewPdfUri ?: lastDecryptedUri ?: if (selectedMetadata?.isEncrypted == false) selectedUris.firstOrNull() else null

    /**
     * Type-safe indicator for status message severity.
     * Replaces brittle startsWith("Error") / contains("❌") string checks in UI.
     */
    val statusLevel: StatusLevel
        get() = when {
            statusMessage == null -> StatusLevel.NONE
            statusMessage.contains("❌") -> StatusLevel.ERROR
            statusMessage.contains("⚠️") -> StatusLevel.WARNING
            statusMessage.contains("✅") -> StatusLevel.SUCCESS
            else -> StatusLevel.INFO
        }
}

/**
 * Severity level for status messages. Used by UI to pick the correct colour
 * without relying on fragile string-prefix checks that break under localisation.
 */
enum class StatusLevel { NONE, SUCCESS, WARNING, ERROR, INFO }
