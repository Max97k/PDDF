# Milestone 1 Handoff Report: MainUiState, UiEffect & MainViewModel Architecture

## 1. Observation

Direct examination of the current codebase revealed the following structural observations:

### Observation 1.1: Monolithic State Spread in `MainViewModel.kt`
- **File**: `app/src/main/java/com/example/MainViewModel.kt` (lines 64–130)
- **Direct Observation**:
  `MainViewModel` currently manages state using more than 20 individual `StateFlow` and `MutableStateFlow` fields:
  ```kotlin
  val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode...
  val savedPasswords: StateFlow<List<PasswordEntity>> = passwordVaultUseCase.allPasswords...
  val selectedUris = MutableStateFlow<List<Uri>>(emptyList())
  val selectedFileNames = MutableStateFlow<List<String>>(emptyList())
  val selectedMetadata = MutableStateFlow<PdfMetadata?>(null)
  val isProcessing = MutableStateFlow(false)
  val statusMessage = MutableStateFlow<String?>(null)
  val lastDecryptedUri = MutableStateFlow<Uri?>(null)
  val conflictMode = MutableStateFlow(ConflictMode.SAVE_AS_COPY)
  val rememberConflictChoice = MutableStateFlow(false)
  val password = MutableStateFlow("")
  val passwordVisible = MutableStateFlow(false)
  val showSavePasswordDialog = MutableStateFlow(false)
  val showPasswordListDialog = MutableStateFlow(false)
  val requestOpenDocumentPicker = MutableStateFlow(false)
  val isAutoUnlocking = MutableStateFlow(false)
  val showAutoUnlockPasswordPrompt = MutableStateFlow(false)
  val autoUnlockTargetUri = MutableStateFlow<Uri?>(null)
  val autoUnlockFileName = MutableStateFlow("")
  val autoUnlockErrorMessage = MutableStateFlow<String?>(null)
  val previewPdfUri = MutableStateFlow<Uri?>(null)
  val pdfUiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
  val batchState = MutableStateFlow(BatchState())
  ```
- **Direct Consequence**: In `MainActivity.kt` (lines 167–187), the Composable layer calls `collectAsStateWithLifecycle()` on 15+ individual flows independently, triggering multiple scattered recompositions across the UI tree.

### Observation 1.2: One-Off Side Effects Mixed into Persistent State
- **File**: `app/src/main/java/com/example/MainViewModel.kt` (lines 104–112)
- **Direct Observation**:
  Events that are single-shot operations (such as opening the SAF file picker) are modeled as boolean state flags:
  ```kotlin
  val requestOpenDocumentPicker = MutableStateFlow(false)
  fun triggerOpenDocumentPicker() { requestOpenDocumentPicker.value = true }
  fun onDocumentPickerLaunched() { requestOpenDocumentPicker.value = false }
  ```
  In `MainActivity.kt` (lines 257–268), this requires an anti-pattern `LaunchedEffect(requestOpenDocumentPicker)` that resets the boolean manually after launching the picker.

### Observation 1.3: SnackBar & Biometric Direct Side-Effects in UI
- **File**: `app/src/main/java/com/example/MainActivity.kt` (lines 479–524, 760–769)
- **Direct Observation**:
  - Biometric authentication logic is embedded directly in `MainActivity.kt` within the `PasswordInputSection` click handler.
  - SnackBar presentation with undo callback is triggered directly inside the composable dialog callback (`SavedPasswordListDialog`).

### Observation 1.4: Domain Model Placement
- **Files**: `app/src/main/java/com/example/PdfMetadata.kt`, `app/src/main/java/com/example/MainViewModel.kt` (lines 32–43), `app/src/main/java/com/example/domain/model/PdfUiState.kt`
- **Direct Observation**:
  - `DecryptStatus` and `ConflictMode` enums are currently declared in `MainViewModel.kt`.
  - `PdfMetadata` is in root package `com.example`.
  - `PdfUiState` is in `com.example.domain.model`.

---

## 2. Logic Chain

1. **Premise 1 (From AGENTS.md §2 & PROJECT.md §1)**:
   - Modern Android Architecture mandates MVVM with Unidirectional Data Flow (UDF).
   - ViewModels must expose a single immutable `StateFlow<MainUiState>` and a single-shot event stream `Flow<UiEffect>`.
   - UI emits intents upward via `onAction(action: MainUiAction)` or lambda callbacks.

2. **Premise 2 (Compose Performance & Stability)**:
   - Replacing 20+ disparate `MutableStateFlow` instances with a single `@Immutable` `MainUiState` data class reduces flow subscription overhead and ensures that leaf composables can safely skip recomposition when their specific slice of state is unchanged.

3. **Premise 3 (Clean Separation of Transient Effects vs Persistent State)**:
   - File picker triggers, directory pickers, create document intents, external viewer launches, biometric authentication triggers, snackbars with actions, and haptic feedback are single-shot operations.
   - Modeling them via `Channel<UiEffect>(Channel.BUFFERED).receiveAsFlow()` guarantees that events are consumed exactly once and are not replayed on configuration changes or recompositions.

4. **Premise 4 (UseCase Orchestration)**:
   - `MainViewModel` cleanly coordinates four domain UseCases:
     - `DecryptPdfUseCase`: Low-level PDFBox decryption and metadata extraction on `Dispatchers.IO`.
     - `AutoUnlockUseCase`: Trying saved vault passwords against incoming external URIs.
     - `BatchProcessUseCase`: In-place or directory batch decryption with progress reporting.
     - `PasswordVaultUseCase`: Room database operations for AES-encrypted password storage.
   - All I/O operations are strictly dispatched to `Dispatchers.IO`, satisfying AGENTS.md §4.

---

## 3. Architecture Specification & Data Definitions

### 3.1 `MainUiState.kt`
**File Target**: `app/src/main/java/com/example/MainUiState.kt`

```kotlin
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
        get() = showPasswordListDialog || showSavePasswordDialog

    val activePreviewUri: Uri?
        get() = previewPdfUri ?: lastDecryptedUri ?: if (selectedMetadata?.isEncrypted == false) selectedUris.firstOrNull() else null
}
```

---

### 3.2 `UiEffect.kt`
**File Target**: `app/src/main/java/com/example/UiEffect.kt`

```kotlin
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
```

---

### 3.3 `MainUiAction.kt`
**File Target**: `app/src/main/java/com/example/MainUiAction.kt`

```kotlin
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
    data object TogglePasswordVisibility : MainUiAction

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
```

---

### 3.4 Refactored `MainViewModel.kt`
**File Target**: `app/src/main/java/com/example/MainViewModel.kt`

```kotlin
package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.domain.usecase.AutoUnlockUseCase
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
import com.example.util.FileUtils
import com.example.util.Result
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DecryptStatus {
    SUCCESS,
    NOT_ENCRYPTED,
    WRONG_PASSWORD,
    UNSUPPORTED_ENCRYPTION,
    ERROR
}

enum class ConflictMode {
    OVERWRITE,
    SAVE_AS_COPY
}

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PasswordRepository = PasswordRepository(
        Room.databaseBuilder(
            application,
            AppDatabase::class.java, "pdf-decryptor-db"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build().passwordDao()
    ),
    private val themePreferences: ThemePreferences = ThemePreferences(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val decryptPdfUseCase: DecryptPdfUseCase = DecryptPdfUseCase(ioDispatcher),
    private val passwordVaultUseCase: PasswordVaultUseCase = PasswordVaultUseCase(repository),
    private val autoUnlockUseCase: AutoUnlockUseCase = AutoUnlockUseCase(decryptPdfUseCase, passwordVaultUseCase, ioDispatcher),
    private val batchProcessUseCase: BatchProcessUseCase = BatchProcessUseCase(decryptPdfUseCase, ioDispatcher)
) : AndroidViewModel(application) {

    // ---------------------------------------------------------------------------------------------
    // UDF State & Effect Streams
    // ---------------------------------------------------------------------------------------------
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<UiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<UiEffect> = _uiEffect.receiveAsFlow()

    // ---------------------------------------------------------------------------------------------
    // Internal State & Background Timing
    // ---------------------------------------------------------------------------------------------
    private var batchJob: Job? = null
    private var backgroundTime: Long = 0
    private val TIMEOUT_MILLIS = 60000L // 60 seconds auto-clear
    private val prefs = application.getSharedPreferences("pdf_decryptor_prefs", Context.MODE_PRIVATE)
    private var pdfBoxInitJob: Job? = null

    init {
        pdfBoxInitJob = viewModelScope.launch(ioDispatcher) {
            PDFBoxResourceLoader.init(application)
        }

        // Restore conflict preferences
        val savedRemember = prefs.getBoolean("remember_conflict_choice", false)
        val savedModeStr = prefs.getString("conflict_mode", ConflictMode.SAVE_AS_COPY.name)
        val savedMode = try {
            ConflictMode.valueOf(savedModeStr!!)
        } catch (_: Exception) {
            ConflictMode.SAVE_AS_COPY
        }
        _uiState.update {
            it.copy(
                rememberConflictChoice = savedRemember,
                conflictMode = if (savedRemember) savedMode else ConflictMode.SAVE_AS_COPY
            )
        }

        // Observe theme preferences
        viewModelScope.launch {
            themePreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        // Observe password vault
        viewModelScope.launch {
            passwordVaultUseCase.allPasswords.collect { result ->
                val passwords = when (result) {
                    is Result.Success -> result.data
                    else -> emptyList()
                }
                _uiState.update { it.copy(savedPasswords = passwords) }
            }
        }
    }

    private suspend fun ensurePdfBoxInitialized() {
        pdfBoxInitJob?.join()
    }

    fun emitEffect(effect: UiEffect) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Unidirectional Action Dispatcher
    // ---------------------------------------------------------------------------------------------
    fun onAction(action: MainUiAction) {
        when (action) {
            is MainUiAction.SelectFiles -> setSelectedUris(action.context, action.uris)
            is MainUiAction.ClearSelectedFiles -> setSelectedUris(action.context, emptyList())
            is MainUiAction.RequestFilePicker -> emitEffect(UiEffect.LaunchFilePicker)
            is MainUiAction.UpdatePassword -> _uiState.update { it.copy(password = action.password) }
            is MainUiAction.TogglePasswordVisibility -> _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
            is MainUiAction.DecryptInPlace -> decryptPdfsInPlace(action.context, uiState.value.selectedUris, uiState.value.password)
            is MainUiAction.DecryptToDirectory -> decryptPdfsToDirectory(action.context, uiState.value.selectedUris, action.outputDirectoryUri, uiState.value.password, uiState.value.conflictMode)
            is MainUiAction.DecryptToUri -> decryptPdfToUri(action.context, uiState.value.selectedUris.firstOrNull() ?: return, action.destUri, uiState.value.password)
            is MainUiAction.RequestSaveAsPicker -> handleSaveAsRequest()
            is MainUiAction.CancelBatch -> cancelBatch()
            is MainUiAction.HandleExternalIntent -> handleExternalPdfIntent(action.context, action.uri, action.onViewerReady)
            is MainUiAction.UnlockWithManualPassword -> unlockWithManualPassword(action.context, uiState.value.autoUnlockTargetUri ?: return, action.enteredPassword, action.rememberPassword, action.onViewerReady)
            is MainUiAction.DismissAutoUnlockPrompt -> dismissAutoUnlockPrompt()
            is MainUiAction.SavePassword -> savePassword(action.name, action.passwordValue)
            is MainUiAction.DeletePassword -> deletePasswordWithUndo(action.entity)
            is MainUiAction.RestorePassword -> restorePassword(action.entity)
            is MainUiAction.SelectSavedPassword -> _uiState.update { it.copy(password = action.passwordValue, showPasswordListDialog = false) }
            is MainUiAction.RequestOpenPasswordList -> emitEffect(UiEffect.TriggerBiometricAuth)
            is MainUiAction.RequestOpenSavePassword -> _uiState.update { it.copy(showSavePasswordDialog = true) }
            is MainUiAction.SetSavePasswordDialogVisible -> _uiState.update { it.copy(showSavePasswordDialog = action.visible) }
            is MainUiAction.SetPasswordListDialogVisible -> _uiState.update { it.copy(showPasswordListDialog = action.visible) }
            is MainUiAction.SetWhatsNewDialogVisible -> _uiState.update { it.copy(showWhatsNewDialog = action.visible) }
            is MainUiAction.SetPreviewPdfUri -> _uiState.update { it.copy(previewPdfUri = action.uri) }
            is MainUiAction.SetTheme -> setTheme(action.mode)
            is MainUiAction.UpdateConflictSettings -> updateConflictSettings(action.mode, action.remember)
            is MainUiAction.CopyUriStream -> copyUriStream(action.context, action.sourceUri, action.destUri)
            is MainUiAction.OpenPdfExternal -> emitEffect(UiEffect.OpenPdfExternally(action.uri))
            is MainUiAction.SharePdf -> emitEffect(UiEffect.SharePdf(action.uri))
            is MainUiAction.OpenDownloads -> emitEffect(UiEffect.OpenFileDownloads)
            is MainUiAction.OpenUrl -> emitEffect(UiEffect.OpenUrl(action.url))
            is MainUiAction.AppBackgrounded -> onAppBackgrounded()
            is MainUiAction.AppForegrounded -> onAppForegrounded()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Semantic Helper Methods (Convenience API & Backward Compatibility)
    // ---------------------------------------------------------------------------------------------
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.saveThemeMode(mode)
        }
    }

    fun updateConflictSettings(mode: ConflictMode, remember: Boolean) {
        _uiState.update { it.copy(conflictMode = mode, rememberConflictChoice = remember) }
        prefs.edit().apply {
            putBoolean("remember_conflict_choice", remember)
            if (remember) putString("conflict_mode", mode.name) else remove("conflict_mode")
            apply()
        }
    }

    fun setSelectedUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            val currentUris = uiState.value.selectedUris
            val removedUris = currentUris.filter { !uris.contains(it) }
            val persistedPermissions = try {
                context.contentResolver.persistedUriPermissions.map { it.uri }
            } catch (_: Exception) {
                emptyList()
            }
            for (removedUri in removedUris) {
                if (persistedPermissions.contains(removedUri)) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            removedUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val pdfPairs = uris.mapNotNull { uri ->
                val name = FileUtils.getFileName(context, uri)
                val type = try {
                    context.contentResolver.getType(uri)
                } catch (_: SecurityException) {
                    null
                }
                val isPdf = name.endsWith(".pdf", ignoreCase = true) ||
                        type?.contains("pdf", ignoreCase = true) == true
                if (isPdf) Pair(uri, name) else null
            }

            val pdfUris = pdfPairs.map { it.first }
            val pdfNames = pdfPairs.map { it.second }

            _uiState.update {
                it.copy(
                    selectedUris = pdfUris,
                    selectedFileNames = pdfNames,
                    selectedMetadata = null,
                    statusMessage = null
                )
            }

            if (pdfUris.isNotEmpty()) {
                checkSelectedPdfs(context, pdfPairs)
            }
        }
    }

    private fun checkSelectedPdfs(context: Context, pdfPairs: List<Pair<Uri, String>>) {
        viewModelScope.launch(ioDispatcher) {
            ensurePdfBoxInitialized()
            val unencryptedNames = mutableListOf<String>()
            val unsupportedNames = mutableListOf<String>()
            var extractedMetadata: PdfMetadata? = null

            for ((uri, fileName) in pdfPairs) {
                if (extractedMetadata == null) {
                    extractedMetadata = decryptPdfUseCase.extractMetadata(context, uri)
                }
                try {
                    decryptPdfUseCase.openSafeInputStream(context, uri)?.use { inputStream ->
                        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(
                            inputStream.buffered(),
                            com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                        )
                        doc.use {
                            if (!it.isEncrypted) unencryptedNames.add(fileName)
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message?.lowercase() ?: ""
                    if (msg.contains("security handler") || msg.contains("cryptfilter") || msg.contains("certificate") || msg.contains("public key") || msg.contains("unsupported")) {
                        unsupportedNames.add(fileName)
                    }
                }
            }

            val warnings = mutableListOf<String>()
            if (unencryptedNames.isNotEmpty()) {
                warnings.add(context.getString(R.string.msg_notice_some_unencrypted, unencryptedNames.joinToString(", ")))
            }
            if (unsupportedNames.isNotEmpty()) {
                warnings.add(context.getString(R.string.msg_warning_unsupported, unsupportedNames.joinToString(", ")))
            }

            _uiState.update {
                it.copy(
                    selectedMetadata = extractedMetadata,
                    statusMessage = if (warnings.isNotEmpty()) warnings.joinToString("\n") else null
                )
            }
        }
    }

    fun handleExternalPdfIntent(
        context: Context,
        uri: Uri,
        onViewerReady: (Uri) -> Unit = {}
    ) {
        viewModelScope.launch {
            val fileName = FileUtils.getFileName(context, uri)
            _uiState.update {
                it.copy(
                    isAutoUnlocking = true,
                    autoUnlockTargetUri = uri,
                    autoUnlockFileName = fileName,
                    autoUnlockErrorMessage = null
                )
            }

            ensurePdfBoxInitialized()

            when (val result = autoUnlockUseCase.tryAutoUnlock(context, uri)) {
                is AutoUnlockUseCase.AutoUnlockResult.NotEncrypted -> {
                    _uiState.update { it.copy(isAutoUnlocking = false, lastDecryptedUri = uri, previewPdfUri = uri) }
                    onViewerReady(uri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword -> {
                    _uiState.update {
                        it.copy(
                            isAutoUnlocking = false,
                            lastDecryptedUri = result.outputUri,
                            previewPdfUri = result.outputUri,
                            statusMessage = context.getString(R.string.summary_decrypted_saved, 1)
                        )
                    }
                    onViewerReady(result.outputUri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.RequireManualPassword,
                is AutoUnlockUseCase.AutoUnlockResult.Error -> {
                    _uiState.update { it.copy(isAutoUnlocking = false, showAutoUnlockPasswordPrompt = true) }
                }
            }
        }
    }

    fun unlockWithManualPassword(
        context: Context,
        uri: Uri,
        enteredPassword: String,
        rememberPassword: Boolean,
        onViewerReady: (Uri) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, autoUnlockErrorMessage = null) }
            ensurePdfBoxInitialized()

            val (status, resultUri) = autoUnlockUseCase.unlockWithManualPassword(
                context = context,
                uri = uri,
                enteredPassword = enteredPassword,
                rememberPassword = rememberPassword,
                fileName = uiState.value.autoUnlockFileName
            )

            _uiState.update { it.copy(isProcessing = false) }

            when (status) {
                DecryptStatus.SUCCESS -> {
                    if (resultUri != null) {
                        _uiState.update {
                            it.copy(
                                lastDecryptedUri = resultUri,
                                previewPdfUri = resultUri,
                                showAutoUnlockPasswordPrompt = false,
                                autoUnlockErrorMessage = null,
                                statusMessage = context.getString(R.string.summary_decrypted_saved, 1)
                            )
                        }
                        onViewerReady(resultUri)
                    }
                }
                DecryptStatus.WRONG_PASSWORD -> {
                    _uiState.update { it.copy(autoUnlockErrorMessage = context.getString(R.string.msg_wrong_password_try_again)) }
                }
                DecryptStatus.NOT_ENCRYPTED -> {
                    _uiState.update {
                        it.copy(
                            lastDecryptedUri = uri,
                            previewPdfUri = uri,
                            showAutoUnlockPasswordPrompt = false
                        )
                    }
                    onViewerReady(uri)
                }
                else -> {
                    _uiState.update { it.copy(autoUnlockErrorMessage = context.getString(R.string.summary_error, 1)) }
                }
            }
        }
    }

    fun dismissAutoUnlockPrompt() {
        _uiState.update { it.copy(showAutoUnlockPasswordPrompt = false, autoUnlockErrorMessage = null) }
    }

    fun decryptPdfsInPlace(context: Context, inputUris: List<Uri>, passwordValue: String) {
        batchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Processing...",
                    batchState = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
                )
            }

            ensurePdfBoxInitialized()

            val result = batchProcessUseCase.processInPlace(
                context = context,
                inputUris = inputUris,
                passwordValue = passwordValue,
                onProgress = { current, _ ->
                    _uiState.update { it.copy(batchState = it.batchState.copy(progress = current)) }
                }
            )

            val summaryList = mutableListOf<String>()
            if (result.successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, result.successCount))
            if (result.notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, result.notEncryptedCount))
            if (result.wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, result.wrongPasswordCount))
            if (result.unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, result.unsupportedCount))
            if (result.errorCount > 0) summaryList.add(context.getString(R.string.summary_error, result.errorCount))
            if (result.cancelledCount > 0) summaryList.add("Cancelled: ${result.cancelledCount} files")

            _uiState.update {
                it.copy(
                    statusMessage = summaryList.joinToString("\n"),
                    lastDecryptedUri = result.lastDecryptedUri ?: it.lastDecryptedUri,
                    isProcessing = false,
                    batchState = BatchState()
                )
            }
        }
    }

    fun decryptAndOverwrite(context: Context, inputUris: List<Uri>, passwordValue: String) {
        decryptPdfsInPlace(context, inputUris, passwordValue)
    }

    fun decryptPdfToUri(context: Context, inputUri: Uri, destUri: Uri, passwordValue: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Processing...") }
            ensurePdfBoxInitialized()

            val status = decryptPdfUseCase.decrypt(context, inputUri, destUri, passwordValue)
            val msg = when (status) {
                DecryptStatus.SUCCESS -> context.getString(R.string.summary_decrypted_saved, 1)
                DecryptStatus.NOT_ENCRYPTED -> context.getString(R.string.summary_not_encrypted, 1)
                DecryptStatus.WRONG_PASSWORD -> context.getString(R.string.summary_wrong_password, 1)
                DecryptStatus.UNSUPPORTED_ENCRYPTION -> context.getString(R.string.summary_unsupported, 1)
                DecryptStatus.ERROR -> context.getString(R.string.summary_error, 1)
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = msg,
                    lastDecryptedUri = if (status == DecryptStatus.SUCCESS) destUri else it.lastDecryptedUri
                )
            }
        }
    }

    fun decryptPdfsToDirectory(
        context: Context,
        inputUris: List<Uri>,
        outputDirectoryUri: Uri,
        passwordValue: String,
        conflictMode: ConflictMode
    ) {
        batchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Processing...",
                    batchState = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
                )
            }

            ensurePdfBoxInitialized()

            val result = batchProcessUseCase.processToDirectory(
                context = context,
                inputUris = inputUris,
                outputDirectoryUri = outputDirectoryUri,
                passwordValue = passwordValue,
                conflictMode = conflictMode,
                onProgress = { current, _ ->
                    _uiState.update { it.copy(batchState = it.batchState.copy(progress = current)) }
                }
            )

            if (result.errorOutputDir) {
                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(R.string.msg_error_output_dir),
                        isProcessing = false,
                        batchState = BatchState()
                    )
                }
                return@launch
            }

            val summaryList = mutableListOf<String>()
            if (result.successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, result.successCount))
            if (result.notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, result.notEncryptedCount))
            if (result.wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, result.wrongPasswordCount))
            if (result.unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, result.unsupportedCount))
            if (result.errorCount > 0) summaryList.add(context.getString(R.string.summary_error, result.errorCount))
            if (result.cancelledCount > 0) summaryList.add("Cancelled: ${result.cancelledCount} files")

            _uiState.update {
                it.copy(
                    statusMessage = summaryList.joinToString("\n"),
                    lastDecryptedUri = result.lastDecryptedUri ?: it.lastDecryptedUri,
                    isProcessing = false,
                    batchState = BatchState()
                )
            }
        }
    }

    private fun handleSaveAsRequest() {
        val state = uiState.value
        if (state.isBatch) {
            emitEffect(UiEffect.LaunchDirectoryPicker)
        } else {
            val fileName = state.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
            emitEffect(UiEffect.LaunchCreateDocument(fileName))
        }
    }

    fun copyUriStream(context: Context, sourceUri: Uri, destUri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            try {
                decryptPdfUseCase.openSafeInputStream(context, sourceUri)?.use { input ->
                    decryptPdfUseCase.openSafeOutputStream(context, destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
    }

    fun savePassword(name: String, passwordValue: String) {
        viewModelScope.launch(ioDispatcher) {
            passwordVaultUseCase.insertPassword(name, passwordValue)
            _uiState.update { it.copy(showSavePasswordDialog = false) }
        }
    }

    fun restorePassword(entity: PasswordEntity) {
        viewModelScope.launch(ioDispatcher) {
            passwordVaultUseCase.insertPassword(entity.name, entity.passwordValue)
        }
    }

    fun deletePassword(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            passwordVaultUseCase.deletePassword(id)
        }
    }

    fun deletePasswordWithUndo(entity: PasswordEntity) {
        deletePassword(entity.id)
        emitEffect(
            UiEffect.ShowSnackbar(
                message = "Password deleted",
                actionLabel = "Undo",
                onAction = { restorePassword(entity) }
            )
        )
    }

    fun onAppBackgrounded() {
        backgroundTime = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (backgroundTime > 0 && System.currentTimeMillis() - backgroundTime > TIMEOUT_MILLIS) {
            clearSensitiveData()
        }
        backgroundTime = 0
    }

    private fun clearSensitiveData() {
        _uiState.update {
            it.copy(
                password = "",
                showSavePasswordDialog = false,
                showPasswordListDialog = false
            )
        }
    }

    internal suspend fun decryptSinglePdf(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): DecryptStatus {
        ensurePdfBoxInitialized()
        return decryptPdfUseCase.decrypt(context, inputUri, outputUri, passwordValue)
    }

    // ---------------------------------------------------------------------------------------------
    // Test Harness Helper (Allows Direct State Injection in Unit/Screenshot Tests)
    // ---------------------------------------------------------------------------------------------
    fun updateStateForTesting(transform: (MainUiState) -> MainUiState) {
        _uiState.update(transform)
    }
}
```

---

## 4. Caveats

1. **Test Suite Adaptation**:
   - Existing unit tests (`MainViewModelTest.kt`) and screenshot tests (`PDFDecryptorScreenshotTest.kt`) that previously mutated or asserted individual fields (e.g. `viewModel.selectedUris.value = listOf(...)`) will be updated to either:
     a) Assert against `viewModel.uiState.value.selectedUris`, or
     b) Use `viewModel.updateStateForTesting { it.copy(...) }`.
2. **Backwards Compatibility Bridge**:
   - If peer modules or tests require transitional property access, computed getters (`val selectedUris: StateFlow<List<Uri>>`) can be provided, but migrating directly to `uiState` is cleaner and eliminates architectural debt.

---

## 5. Conclusion

The designed UDF architecture:
- Unifies state into a single immutable `@Immutable MainUiState` data class.
- Replaces polling/boolean side effects with a robust `Channel<UiEffect>` event channel.
- Implements `onAction(MainUiAction)` to streamline UI intent processing while preserving clean domain UseCase isolation.
- Guarantees `Dispatchers.IO` concurrency isolation and memory protection.

---

## 6. Verification Method

1. **Compilation & Build Check**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
2. **Unit Test Execution**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
3. **Roborazzi Screenshot Tests**:
   ```powershell
   .\gradlew.bat :app:recordRoborazziDebug
   ```
4. **Code Inspection**:
   - Confirm `MainUiState.kt` contains `@Immutable` and includes all required state slices.
   - Confirm `UiEffect.kt` encapsulates one-off actions (`ShowSnackbar`, `LaunchFilePicker`, `TriggerBiometricAuth`).
   - Confirm `MainViewModel.kt` only exposes `uiState: StateFlow<MainUiState>` and `uiEffect: Flow<UiEffect>`.
