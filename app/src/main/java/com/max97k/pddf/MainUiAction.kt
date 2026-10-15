package com.max97k.pddf

import android.net.Uri
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.data.ThemeMode
import com.max97k.pddf.data.DensityMode

/**
 * Sealed interface representing all user intents flowing upwards from UI to ViewModel.
 * Completely free of Activity and Context references to prevent memory leaks.
 */
sealed interface MainUiAction {
    // File selection
    data class SelectFiles(val uris: List<Uri>) : MainUiAction
    data object ClearSelectedFiles : MainUiAction
    data object RequestFilePicker : MainUiAction

    // Password input
    data class UpdatePassword(val password: String) : MainUiAction
    data class SetPassword(val password: String) : MainUiAction
    data object ClearPassword : MainUiAction
    data object TogglePasswordVisibility : MainUiAction
    data object TogglePasswordVisible : MainUiAction

    // Decryption actions
    data object DecryptInPlace : MainUiAction
    data class DecryptToDirectory(val outputDirectoryUri: Uri) : MainUiAction
    data class DecryptToZip(val destUri: Uri) : MainUiAction
    data class DecryptToUri(val destUri: Uri) : MainUiAction
    data class DecryptAndPreview(val uri: Uri, val passwordValue: String) : MainUiAction
    data object RequestSaveAsPicker : MainUiAction
    data object RequestExportCsv : MainUiAction
    data object RequestExportJson : MainUiAction
    data class ExportReportToUri(val uri: Uri, val format: String) : MainUiAction
    data object CancelBatch : MainUiAction
    data class ReversePages(val uri: Uri) : MainUiAction

    // Page Stamping
    data class RequestStampSaveAsPicker(val config: com.max97k.pddf.domain.model.StampConfig) : MainUiAction
    data class StampPages(val destUri: Uri, val config: com.max97k.pddf.domain.model.StampConfig) : MainUiAction

    // Attachments
    data class RequestSaveAttachment(val attachmentName: String) : MainUiAction
    data class SaveAttachmentToUri(val destUri: Uri, val attachmentName: String) : MainUiAction
    data object RequestSaveAllAttachments : MainUiAction
    data class SaveAllAttachmentsToDirectory(val directoryUri: Uri) : MainUiAction

    // External Intent & Auto-Unlock
    data class StartAutoUnlock(val uri: Uri) : MainUiAction
    data class HandleExternalIntent(
        val uri: Uri,
        val onViewerReady: (Uri) -> Unit = {}
    ) : MainUiAction
    data class UnlockWithManualPassword(
        val enteredPassword: String,
        val rememberPassword: Boolean,
        val onViewerReady: (Uri) -> Unit = {}
    ) : MainUiAction
    data object DismissAutoUnlockPrompt : MainUiAction

    // Password Vault Actions
    data class SavePassword(val name: String, val passwordValue: String) : MainUiAction
    data class DeletePassword(val entity: PasswordEntity) : MainUiAction
    data class RestorePassword(val entity: PasswordEntity) : MainUiAction
    data class SelectSavedPassword(val passwordValue: String) : MainUiAction
    data object RequestOpenPasswordList : MainUiAction
    data object RequestOpenSavePassword : MainUiAction

    // Dialog & Preview Visibility
    data class SetSavePasswordDialogVisible(val visible: Boolean) : MainUiAction
    data class SetPasswordListDialogVisible(val visible: Boolean) : MainUiAction
    data class SetWhatsNewDialogVisible(val visible: Boolean) : MainUiAction
    data class SetPreviewPdfUri(val uri: Uri?) : MainUiAction
    data class RotatePreview(val degrees: Int) : MainUiAction
    data class SetStampDialogVisible(val visible: Boolean) : MainUiAction

    // Settings
    data class SetTheme(val mode: ThemeMode) : MainUiAction
    data class SetDensityMode(val mode: DensityMode) : MainUiAction
    data class UpdateConflictSettings(val mode: ConflictMode, val remember: Boolean) : MainUiAction
    data class SetScreenshotProtection(val enabled: Boolean) : MainUiAction

    // External Navigation / System Actions
    data class CopyUriStream(val sourceUri: Uri, val destUri: Uri) : MainUiAction
    data class OpenPdfExternal(val uri: Uri) : MainUiAction
    data class SharePdf(val uri: Uri) : MainUiAction
    data object OpenDownloads : MainUiAction
    data class OpenUrl(val url: String) : MainUiAction

    // Watermark
    data class AddWatermark(val uri: Uri, val text: String) : MainUiAction

    // App Lifecycle
    data object AppBackgrounded : MainUiAction
    data object AppForegrounded : MainUiAction

    // In-App Updates
    data object CheckForUpdates : MainUiAction
    data class StartFlexibleUpdate(val launcher: Any) : MainUiAction
    data object CompleteAppUpdate : MainUiAction
    data object ResetAppUpdateStatus : MainUiAction
}
