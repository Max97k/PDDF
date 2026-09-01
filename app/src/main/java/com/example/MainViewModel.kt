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
import com.example.domain.model.PdfUiState
import com.example.domain.usecase.AutoUnlockUseCase
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
import com.example.util.FileUtils
import com.example.util.MemoryUtils
import com.example.util.Result
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    val savedPasswords: StateFlow<List<PasswordEntity>> = passwordVaultUseCase.allPasswords
        .map { result ->
            when (result) {
                is Result.Success -> result.data
                else -> emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // ---------------------------------------------------------------------------------------------
    // UDF State & Effect Streams
    // ---------------------------------------------------------------------------------------------
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = kotlinx.coroutines.flow.combine(
        _uiState,
        themeMode,
        savedPasswords
    ) { baseState, theme, passwords ->
        baseState.copy(
            themeMode = theme,
            savedPasswords = passwords
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    private val _uiEffect = Channel<UiEffect>(Channel.BUFFERED)
    val uiEffect: Flow<UiEffect> = _uiEffect.receiveAsFlow()

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

    // Legacy UDF UI state for boundary / backward compatibility
    val pdfUiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)

    val batchState = MutableStateFlow(BatchState())
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
        rememberConflictChoice.value = savedRemember
        val savedModeStr = prefs.getString("conflict_mode", ConflictMode.SAVE_AS_COPY.name)
        val savedMode = try {
            ConflictMode.valueOf(savedModeStr!!)
        } catch (_: Exception) {
            ConflictMode.SAVE_AS_COPY
        }
        conflictMode.value = if (savedRemember) savedMode else ConflictMode.SAVE_AS_COPY

        _uiState.update {
            it.copy(
                rememberConflictChoice = savedRemember,
                conflictMode = if (savedRemember) savedMode else ConflictMode.SAVE_AS_COPY
            )
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
    // Unidirectional Action Dispatcher (UDF Intent Handler)
    // ---------------------------------------------------------------------------------------------
    fun onAction(action: MainUiAction) {
        when (action) {
            is MainUiAction.SelectFiles -> setSelectedUris(action.context, action.uris)
            is MainUiAction.ClearSelectedFiles -> setSelectedUris(action.context, emptyList())
            is MainUiAction.RequestFilePicker -> triggerOpenDocumentPicker()
            is MainUiAction.UpdatePassword -> {
                password.value = action.password
                _uiState.update { it.copy(password = action.password) }
            }
            is MainUiAction.SetPassword -> {
                password.value = action.password
                _uiState.update { it.copy(password = action.password) }
            }
            is MainUiAction.TogglePasswordVisibility -> {
                val newVisible = !passwordVisible.value
                passwordVisible.value = newVisible
                _uiState.update { it.copy(passwordVisible = newVisible) }
            }
            is MainUiAction.TogglePasswordVisible -> {
                val newVisible = !passwordVisible.value
                passwordVisible.value = newVisible
                _uiState.update { it.copy(passwordVisible = newVisible) }
            }
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
            is MainUiAction.SelectSavedPassword -> {
                password.value = action.passwordValue
                showPasswordListDialog.value = false
                _uiState.update { it.copy(password = action.passwordValue, showPasswordListDialog = false) }
            }
            is MainUiAction.RequestOpenPasswordList -> emitEffect(UiEffect.TriggerBiometricAuth)
            is MainUiAction.RequestOpenSavePassword -> {
                showSavePasswordDialog.value = true
                _uiState.update { it.copy(showSavePasswordDialog = true) }
            }
            is MainUiAction.SetSavePasswordDialogVisible -> {
                showSavePasswordDialog.value = action.visible
                _uiState.update { it.copy(showSavePasswordDialog = action.visible) }
            }
            is MainUiAction.SetPasswordListDialogVisible -> {
                showPasswordListDialog.value = action.visible
                _uiState.update { it.copy(showPasswordListDialog = action.visible) }
            }
            is MainUiAction.SetWhatsNewDialogVisible -> {
                _uiState.update { it.copy(showWhatsNewDialog = action.visible) }
            }
            is MainUiAction.SetPreviewPdfUri -> {
                previewPdfUri.value = action.uri
                _uiState.update { it.copy(previewPdfUri = action.uri) }
            }
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

    private fun handleSaveAsRequest() {
        val state = uiState.value
        if (state.isBatch) {
            emitEffect(UiEffect.LaunchDirectoryPicker)
        } else {
            val fileName = state.selectedFileNames.firstOrNull() ?: "decrypted.pdf"
            emitEffect(UiEffect.LaunchCreateDocument(fileName))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Semantic Helper Methods
    // ---------------------------------------------------------------------------------------------
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.saveThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun triggerOpenDocumentPicker() {
        requestOpenDocumentPicker.value = true
        emitEffect(UiEffect.LaunchFilePicker)
    }

    fun onDocumentPickerLaunched() {
        requestOpenDocumentPicker.value = false
    }

    fun updateConflictSettings(mode: ConflictMode, remember: Boolean) {
        conflictMode.value = mode
        rememberConflictChoice.value = remember
        _uiState.update { it.copy(conflictMode = mode, rememberConflictChoice = remember) }
        prefs.edit().apply {
            putBoolean("remember_conflict_choice", remember)
            if (remember) putString("conflict_mode", mode.name) else remove("conflict_mode")
            apply()
        }
    }

    fun setSelectedUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            val currentUris = selectedUris.value
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

            selectedUris.value = pdfUris
            selectedFileNames.value = pdfNames
            selectedMetadata.value = null
            statusMessage.value = null

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

            selectedMetadata.value = extractedMetadata

            val warnings = mutableListOf<String>()
            if (unencryptedNames.isNotEmpty()) {
                warnings.add(context.getString(R.string.msg_notice_some_unencrypted, unencryptedNames.joinToString(", ")))
            }
            if (unsupportedNames.isNotEmpty()) {
                warnings.add(context.getString(R.string.msg_warning_unsupported, unsupportedNames.joinToString(", ")))
            }

            val finalMsg = if (warnings.isNotEmpty()) warnings.joinToString("\n") else null
            statusMessage.value = finalMsg

            _uiState.update {
                it.copy(
                    selectedMetadata = extractedMetadata,
                    statusMessage = finalMsg
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
            isAutoUnlocking.value = true
            autoUnlockTargetUri.value = uri
            autoUnlockFileName.value = fileName
            autoUnlockErrorMessage.value = null

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
                    isAutoUnlocking.value = false
                    lastDecryptedUri.value = uri
                    previewPdfUri.value = uri
                    _uiState.update { it.copy(isAutoUnlocking = false, lastDecryptedUri = uri, previewPdfUri = uri) }
                    onViewerReady(uri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword -> {
                    val msg = context.getString(R.string.summary_decrypted_saved, 1)
                    isAutoUnlocking.value = false
                    lastDecryptedUri.value = result.outputUri
                    previewPdfUri.value = result.outputUri
                    statusMessage.value = msg
                    _uiState.update {
                        it.copy(
                            isAutoUnlocking = false,
                            lastDecryptedUri = result.outputUri,
                            previewPdfUri = result.outputUri,
                            statusMessage = msg
                        )
                    }
                    onViewerReady(result.outputUri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.RequireManualPassword,
                is AutoUnlockUseCase.AutoUnlockResult.Error -> {
                    isAutoUnlocking.value = false
                    showAutoUnlockPasswordPrompt.value = true
                    _uiState.update { it.copy(isAutoUnlocking = false, showAutoUnlockPasswordPrompt = true) }
                }
            }
        }
    }

    fun startAutoUnlockFlow(context: Context, uri: Uri, onViewerReady: (Uri) -> Unit = {}) {
        handleExternalPdfIntent(context, uri, onViewerReady)
    }

    fun unlockWithManualPassword(
        context: Context,
        uri: Uri,
        enteredPassword: String,
        rememberPassword: Boolean,
        onViewerReady: (Uri) -> Unit = {}
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            autoUnlockErrorMessage.value = null
            _uiState.update { it.copy(isProcessing = true, autoUnlockErrorMessage = null) }

            ensurePdfBoxInitialized()

            val (status, resultUri) = autoUnlockUseCase.unlockWithManualPassword(
                context = context,
                uri = uri,
                enteredPassword = enteredPassword,
                rememberPassword = rememberPassword,
                fileName = autoUnlockFileName.value
            )

            isProcessing.value = false
            _uiState.update { it.copy(isProcessing = false) }

            when (status) {
                DecryptStatus.SUCCESS -> {
                    if (resultUri != null) {
                        val msg = context.getString(R.string.summary_decrypted_saved, 1)
                        lastDecryptedUri.value = resultUri
                        previewPdfUri.value = resultUri
                        showAutoUnlockPasswordPrompt.value = false
                        autoUnlockErrorMessage.value = null
                        statusMessage.value = msg
                        _uiState.update {
                            it.copy(
                                lastDecryptedUri = resultUri,
                                previewPdfUri = resultUri,
                                showAutoUnlockPasswordPrompt = false,
                                autoUnlockErrorMessage = null,
                                statusMessage = msg
                            )
                        }
                        onViewerReady(resultUri)
                    }
                }
                DecryptStatus.WRONG_PASSWORD -> {
                    val errMsg = context.getString(R.string.msg_wrong_password_try_again)
                    autoUnlockErrorMessage.value = errMsg
                    _uiState.update { it.copy(autoUnlockErrorMessage = errMsg) }
                }
                DecryptStatus.NOT_ENCRYPTED -> {
                    lastDecryptedUri.value = uri
                    previewPdfUri.value = uri
                    showAutoUnlockPasswordPrompt.value = false
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
                    val errMsg = context.getString(R.string.summary_error, 1)
                    autoUnlockErrorMessage.value = errMsg
                    _uiState.update { it.copy(autoUnlockErrorMessage = errMsg) }
                }
            }
        }
    }

    fun dismissAutoUnlockPrompt() {
        showAutoUnlockPasswordPrompt.value = false
        autoUnlockErrorMessage.value = null
        _uiState.update { it.copy(showAutoUnlockPasswordPrompt = false, autoUnlockErrorMessage = null) }
    }

    fun decryptPdfsInPlace(context: Context, inputUris: List<Uri>, passwordValue: String) {
        batchJob = viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Processing..."
            val initBatch = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
            batchState.value = initBatch
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Processing...",
                    batchState = initBatch
                )
            }

            ensurePdfBoxInitialized()

            val result = batchProcessUseCase.processInPlace(
                context = context,
                inputUris = inputUris,
                passwordValue = passwordValue,
                onProgress = { current, _ ->
                    val updatedBatch = batchState.value.copy(progress = current)
                    batchState.value = updatedBatch
                    _uiState.update { it.copy(batchState = updatedBatch) }
                }
            )

            val summaryList = mutableListOf<String>()
            if (result.successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, result.successCount))
            if (result.notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, result.notEncryptedCount))
            if (result.wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, result.wrongPasswordCount))
            if (result.unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, result.unsupportedCount))
            if (result.errorCount > 0) summaryList.add(context.getString(R.string.summary_error, result.errorCount))
            if (result.cancelledCount > 0) summaryList.add("Cancelled: ${result.cancelledCount} files")

            val finalMsg = summaryList.joinToString("\n")
            statusMessage.value = finalMsg
            if (result.lastDecryptedUri != null) {
                lastDecryptedUri.value = result.lastDecryptedUri
            }
            isProcessing.value = false
            batchState.value = BatchState()

            _uiState.update {
                it.copy(
                    statusMessage = finalMsg,
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
            isProcessing.value = true
            statusMessage.value = "Processing..."
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

            statusMessage.value = msg
            if (status == DecryptStatus.SUCCESS) {
                lastDecryptedUri.value = destUri
            }
            isProcessing.value = false

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = msg,
                    lastDecryptedUri = if (status == DecryptStatus.SUCCESS) destUri else it.lastDecryptedUri
                )
            }
        }
    }

    fun decryptAndSaveAs(context: Context, inputUri: Uri?, destUri: Uri, passwordValue: String) {
        if (inputUri != null) {
            decryptPdfToUri(context, inputUri, destUri, passwordValue)
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
            isProcessing.value = true
            statusMessage.value = "Processing..."
            val initBatch = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
            batchState.value = initBatch
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Processing...",
                    batchState = initBatch
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
                    val updatedBatch = batchState.value.copy(progress = current)
                    batchState.value = updatedBatch
                    _uiState.update { it.copy(batchState = updatedBatch) }
                }
            )

            if (result.errorOutputDir) {
                val errOutputDir = context.getString(R.string.msg_error_output_dir)
                statusMessage.value = errOutputDir
                isProcessing.value = false
                batchState.value = BatchState()
                _uiState.update {
                    it.copy(
                        statusMessage = errOutputDir,
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

            val finalMsg = summaryList.joinToString("\n")
            statusMessage.value = finalMsg
            if (result.lastDecryptedUri != null) {
                lastDecryptedUri.value = result.lastDecryptedUri
            }
            isProcessing.value = false
            batchState.value = BatchState()

            _uiState.update {
                it.copy(
                    statusMessage = finalMsg,
                    lastDecryptedUri = result.lastDecryptedUri ?: it.lastDecryptedUri,
                    isProcessing = false,
                    batchState = BatchState()
                )
            }
        }
    }

    fun decryptMultiplePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputDirectoryUri: Uri,
        passwordValue: String,
        prefix: String = "",
        overwrite: Boolean = false
    ) {
        val mode = if (overwrite) ConflictMode.OVERWRITE else ConflictMode.SAVE_AS_COPY
        decryptPdfsToDirectory(context, inputUris, outputDirectoryUri, passwordValue, mode)
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
            showSavePasswordDialog.value = false
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
        val currentChars = password.value.toCharArray()
        MemoryUtils.wipe(currentChars)
        password.value = ""
        showSavePasswordDialog.value = false
        showPasswordListDialog.value = false
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
    // Test Harness Helper (Allows Direct State Injection in Unit / Screenshot Tests)
    // ---------------------------------------------------------------------------------------------
    fun updateStateForTesting(transform: (MainUiState) -> MainUiState) {
        _uiState.update(transform)
        val s = _uiState.value
        selectedUris.value = s.selectedUris
        selectedFileNames.value = s.selectedFileNames
        selectedMetadata.value = s.selectedMetadata
        password.value = s.password
        passwordVisible.value = s.passwordVisible
        isProcessing.value = s.isProcessing
        statusMessage.value = s.statusMessage
        lastDecryptedUri.value = s.lastDecryptedUri
        previewPdfUri.value = s.previewPdfUri
        batchState.value = s.batchState
        isAutoUnlocking.value = s.isAutoUnlocking
        showAutoUnlockPasswordPrompt.value = s.showAutoUnlockPasswordPrompt
        autoUnlockTargetUri.value = s.autoUnlockTargetUri
        autoUnlockFileName.value = s.autoUnlockFileName
        autoUnlockErrorMessage.value = s.autoUnlockErrorMessage
        showSavePasswordDialog.value = s.showSavePasswordDialog
        showPasswordListDialog.value = s.showPasswordListDialog
        conflictMode.value = s.conflictMode
        rememberConflictChoice.value = s.rememberConflictChoice
    }
}
