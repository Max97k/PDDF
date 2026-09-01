package com.example

import android.net.Uri
import androidx.compose.material3.SnackbarDuration

/**
 * Sealed interface representing single-shot, one-off UI side-effects.
 * Delivered through a buffered Channel and consumed via Flow in LaunchedEffect.
 */
sealed interface UiEffect {

    /**
     * Display a transient snackbar with an optional action callback (e.g. Undo delete).
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short,
        val onAction: (() -> Unit)? = null
    ) : UiEffect

    /**
     * Display a simple Android Toast.
     */
    data class ShowToast(val message: String) : UiEffect

    /**
     * Launch the system Storage Access Framework (SAF) document picker for PDF selection.
     */
    data object LaunchFilePicker : UiEffect

    /**
     * Launch the system SAF document tree picker for selecting an output folder in batch mode.
     */
    data object LaunchDirectoryPicker : UiEffect

    /**
     * Launch the system SAF create document picker for saving a single decrypted PDF.
     */
    data class LaunchCreateDocument(val defaultFileName: String) : UiEffect

    /**
     * Launch the system create document picker from the PDF preview dialog.
     */
    data class LaunchSavePreviewPdf(val sourceUri: Uri, val defaultFileName: String) : UiEffect

    /**
     * Trigger hardware-backed BiometricPrompt authentication.
     */
    data object TriggerBiometricAuth : UiEffect

    /**
     * Open an external PDF viewer using Intent.ACTION_VIEW.
     */
    data class OpenPdfExternally(val uri: Uri) : UiEffect

    /**
     * Share a decrypted PDF file using Intent.ACTION_SEND.
     */
    data class SharePdf(val uri: Uri) : UiEffect

    /**
     * Open the system Download Manager / File Explorer.
     */
    data object OpenFileDownloads : UiEffect

    /**
     * Open a web URL (e.g. GitHub repository link).
     */
    data class OpenUrl(val url: String) : UiEffect

    /**
     * Trigger tactile haptic feedback.
     */
    data class PerformHaptic(val type: HapticType) : UiEffect {
        enum class HapticType {
            CONFIRM,
            REJECT,
            CLICK,
            TEXT_HANDLE_MOVE
        }
    }
}
