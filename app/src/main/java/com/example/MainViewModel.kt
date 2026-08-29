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
import com.example.util.Result
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.saveThemeMode(mode)
        }
    }

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

    val isAutoUnlocking = MutableStateFlow(false)
    val showAutoUnlockPasswordPrompt = MutableStateFlow(false)
    val autoUnlockTargetUri = MutableStateFlow<Uri?>(null)
    val autoUnlockFileName = MutableStateFlow("")
    val autoUnlockErrorMessage = MutableStateFlow<String?>(null)
    val previewPdfUri = MutableStateFlow<Uri?>(null)

    // Unified UDF UI state
    val pdfUiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)

    data class BatchState(
        val isProcessing: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0
    )
    val batchState = MutableStateFlow(BatchState())
    private var batchJob: Job? = null

    fun cancelBatch() {
        batchJob?.cancel()
    }

    private var backgroundTime: Long = 0
    private val TIMEOUT_MILLIS = 60000L // 60 seconds

    private val prefs = application.getSharedPreferences("pdf_decryptor_prefs", Context.MODE_PRIVATE)

    private var pdfBoxInitJob: Job? = null

    init {
        pdfBoxInitJob = viewModelScope.launch(ioDispatcher) {
            PDFBoxResourceLoader.init(application)
        }
        val savedRemember = prefs.getBoolean("remember_conflict_choice", false)
        rememberConflictChoice.value = savedRemember
        if (savedRemember) {
            val savedModeStr = prefs.getString("conflict_mode", ConflictMode.SAVE_AS_COPY.name)
            conflictMode.value = try {
                ConflictMode.valueOf(savedModeStr!!)
            } catch (_: Exception) {
                ConflictMode.SAVE_AS_COPY
            }
        }
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
        password.value = ""
        showSavePasswordDialog.value = false
        showPasswordListDialog.value = false
    }

    private suspend fun ensurePdfBoxInitialized() {
        pdfBoxInitJob?.join()
    }

    fun updateConflictSettings(mode: ConflictMode, remember: Boolean) {
        conflictMode.value = mode
        rememberConflictChoice.value = remember
        prefs.edit().apply {
            putBoolean("remember_conflict_choice", remember)
            if (remember) {
                putString("conflict_mode", mode.name)
            } else {
                remove("conflict_mode")
            }
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
                            if (!it.isEncrypted) {
                                unencryptedNames.add(fileName)
                            }
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

            if (warnings.isNotEmpty()) {
                statusMessage.value = warnings.joinToString("\n")
            }
        }
    }

    fun handleExternalPdfIntent(
        context: Context,
        uri: Uri,
        onViewerReady: (Uri) -> Unit = {}
    ) {
        viewModelScope.launch {
            isAutoUnlocking.value = true
            autoUnlockTargetUri.value = uri
            val fileName = FileUtils.getFileName(context, uri)
            autoUnlockFileName.value = fileName
            autoUnlockErrorMessage.value = null

            ensurePdfBoxInitialized()

            when (val result = autoUnlockUseCase.tryAutoUnlock(context, uri)) {
                is AutoUnlockUseCase.AutoUnlockResult.NotEncrypted -> {
                    isAutoUnlocking.value = false
                    lastDecryptedUri.value = uri
                    previewPdfUri.value = uri
                    onViewerReady(uri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword -> {
                    isAutoUnlocking.value = false
                    lastDecryptedUri.value = result.outputUri
                    previewPdfUri.value = result.outputUri
                    statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                    onViewerReady(result.outputUri)
                }
                is AutoUnlockUseCase.AutoUnlockResult.RequireManualPassword -> {
                    isAutoUnlocking.value = false
                    showAutoUnlockPasswordPrompt.value = true
                }
                is AutoUnlockUseCase.AutoUnlockResult.Error -> {
                    isAutoUnlocking.value = false
                    showAutoUnlockPasswordPrompt.value = true
                }
            }
        }
    }

    // Legacy alias for compatibility
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

            ensurePdfBoxInitialized()

            val (status, resultUri) = autoUnlockUseCase.unlockWithManualPassword(
                context = context,
                uri = uri,
                enteredPassword = enteredPassword,
                rememberPassword = rememberPassword,
                fileName = autoUnlockFileName.value
            )

            isProcessing.value = false
            when (status) {
                DecryptStatus.SUCCESS -> {
                    if (resultUri != null) {
                        lastDecryptedUri.value = resultUri
                        previewPdfUri.value = resultUri
                        showAutoUnlockPasswordPrompt.value = false
                        autoUnlockErrorMessage.value = null
                        statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                        onViewerReady(resultUri)
                    }
                }
                DecryptStatus.WRONG_PASSWORD -> {
                    autoUnlockErrorMessage.value = context.getString(R.string.msg_wrong_password_try_again)
                }
                DecryptStatus.NOT_ENCRYPTED -> {
                    lastDecryptedUri.value = uri
                    previewPdfUri.value = uri
                    showAutoUnlockPasswordPrompt.value = false
                    onViewerReady(uri)
                }
                else -> {
                    autoUnlockErrorMessage.value = context.getString(R.string.summary_error, 1)
                }
            }
        }
    }

    fun dismissAutoUnlockPrompt() {
        showAutoUnlockPasswordPrompt.value = false
        autoUnlockErrorMessage.value = null
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

    fun decryptPdfsInPlace(context: Context, inputUris: List<Uri>, passwordValue: String) {
        batchJob = viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Processing..."
            batchState.value = BatchState(isProcessing = true, progress = 0, total = inputUris.size)

            ensurePdfBoxInitialized()

            val result = batchProcessUseCase.processInPlace(
                context = context,
                inputUris = inputUris,
                passwordValue = passwordValue,
                onProgress = { current, _ ->
                    batchState.value = batchState.value.copy(progress = current)
                }
            )

            val summaryList = mutableListOf<String>()
            if (result.successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, result.successCount))
            if (result.notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, result.notEncryptedCount))
            if (result.wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, result.wrongPasswordCount))
            if (result.unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, result.unsupportedCount))
            if (result.errorCount > 0) summaryList.add(context.getString(R.string.summary_error, result.errorCount))
            if (result.cancelledCount > 0) summaryList.add("Cancelled: ${result.cancelledCount} files")

            statusMessage.value = summaryList.joinToString("\n")
            if (result.lastDecryptedUri != null) {
                lastDecryptedUri.value = result.lastDecryptedUri
            }
            isProcessing.value = false
            batchState.value = BatchState()
        }
    }

    fun decryptAndOverwrite(context: Context, inputUris: List<Uri>, passwordValue: String) {
        decryptPdfsInPlace(context, inputUris, passwordValue)
    }

    fun decryptPdfToUri(context: Context, inputUri: Uri, destUri: Uri, passwordValue: String) {
        viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Processing..."
            ensurePdfBoxInitialized()
            val status = decryptPdfUseCase.decrypt(context, inputUri, destUri, passwordValue)
            when (status) {
                DecryptStatus.SUCCESS -> {
                    statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                    lastDecryptedUri.value = destUri
                }
                DecryptStatus.NOT_ENCRYPTED -> statusMessage.value = context.getString(R.string.summary_not_encrypted, 1)
                DecryptStatus.WRONG_PASSWORD -> statusMessage.value = context.getString(R.string.summary_wrong_password, 1)
                DecryptStatus.UNSUPPORTED_ENCRYPTION -> statusMessage.value = context.getString(R.string.summary_unsupported, 1)
                DecryptStatus.ERROR -> statusMessage.value = context.getString(R.string.summary_error, 1)
            }
            isProcessing.value = false
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
            batchState.value = BatchState(isProcessing = true, progress = 0, total = inputUris.size)

            ensurePdfBoxInitialized()

            val result = batchProcessUseCase.processToDirectory(
                context = context,
                inputUris = inputUris,
                outputDirectoryUri = outputDirectoryUri,
                passwordValue = passwordValue,
                conflictMode = conflictMode,
                onProgress = { current, _ ->
                    batchState.value = batchState.value.copy(progress = current)
                }
            )

            if (result.errorOutputDir) {
                statusMessage.value = context.getString(R.string.msg_error_output_dir)
                isProcessing.value = false
                batchState.value = BatchState()
                return@launch
            }

            val summaryList = mutableListOf<String>()
            if (result.successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, result.successCount))
            if (result.notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, result.notEncryptedCount))
            if (result.wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, result.wrongPasswordCount))
            if (result.unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, result.unsupportedCount))
            if (result.errorCount > 0) summaryList.add(context.getString(R.string.summary_error, result.errorCount))
            if (result.cancelledCount > 0) summaryList.add("Cancelled: ${result.cancelledCount} files")

            statusMessage.value = summaryList.joinToString("\n")
            if (result.lastDecryptedUri != null) {
                lastDecryptedUri.value = result.lastDecryptedUri
            }
            isProcessing.value = false
            batchState.value = BatchState()
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

    fun savePassword(name: String, passwordValue: String) {
        viewModelScope.launch(ioDispatcher) {
            passwordVaultUseCase.insertPassword(name, passwordValue)
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

    internal suspend fun decryptSinglePdf(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): DecryptStatus {
        ensurePdfBoxInitialized()
        return decryptPdfUseCase.decrypt(context, inputUri, outputUri, passwordValue)
    }
}
