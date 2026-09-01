package com.example

import android.content.Context
import android.net.Uri
import com.example.data.PasswordEntity
import com.example.data.ThemeMode

/**
 * Sealed interface representing all user intents flowing upwards from UI to ViewModel.
 */
sealed interface MainUiAction {
    // File selection
    data class SelectFiles(val context: Context, val uris: List<Uri>) : MainUiAction
    data class ClearSelectedFiles(val context: Context) : MainUiAction
    data object RequestFilePicker : MainUiAction

    // Password input
    data class UpdatePassword(val password: String) : MainUiAction
    data class SetPassword(val password: String) : MainUiAction
    data object TogglePasswordVisibility : MainUiAction
    data object TogglePasswordVisible : MainUiAction

    // Decryption actions
    data class DecryptInPlace(val context: Context) : MainUiAction
    data class DecryptToDirectory(val context: Context, val outputDirectoryUri: Uri) : MainUiAction
    data class DecryptToUri(val context: Context, val destUri: Uri) : MainUiAction
    data object RequestSaveAsPicker : MainUiAction
    data object CancelBatch : MainUiAction

    // External Intent & Auto-Unlock
    data class HandleExternalIntent(
        val context: Context,
        val uri: Uri,
        val onViewerReady: (Uri) -> Unit = {}
    ) : MainUiAction
    data class UnlockWithManualPassword(
        val context: Context,
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

    // Settings
    data class SetTheme(val mode: ThemeMode) : MainUiAction
    data class UpdateConflictSettings(val mode: ConflictMode, val remember: Boolean) : MainUiAction

    // External Navigation / System Actions
    data class CopyUriStream(val context: Context, val sourceUri: Uri, val destUri: Uri) : MainUiAction
    data class OpenPdfExternal(val uri: Uri) : MainUiAction
    data class SharePdf(val uri: Uri) : MainUiAction
    data object OpenDownloads : MainUiAction
    data class OpenUrl(val url: String) : MainUiAction

    // App Lifecycle
    data object AppBackgrounded : MainUiAction
    data object AppForegrounded : MainUiAction
}
