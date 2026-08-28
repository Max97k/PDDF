package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.util.FileUtils
import com.example.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val themePreferences: ThemePreferences = ThemePreferences(application)
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

    val savedPasswords: StateFlow<List<PasswordEntity>> = repository.allPasswords
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

    data class BatchState(
        val isProcessing: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0
    )
    val batchState = MutableStateFlow(BatchState())
    private var batchJob: kotlinx.coroutines.Job? = null

    fun cancelBatch() {
        batchJob?.cancel()
    }

    private var backgroundTime: Long = 0
    private val TIMEOUT_MILLIS = 60000L // 60 seconds

    private val prefs = application.getSharedPreferences("pdf_decryptor_prefs", Context.MODE_PRIVATE)

    private var pdfBoxInitJob: kotlinx.coroutines.Job? = null

    init {
        pdfBoxInitJob = viewModelScope.launch(Dispatchers.IO) {
            PDFBoxResourceLoader.init(application)
        }
        val savedRemember = prefs.getBoolean("remember_conflict_choice", false)
        rememberConflictChoice.value = savedRemember
        if (savedRemember) {
            val savedModeStr = prefs.getString("conflict_mode", ConflictMode.SAVE_AS_COPY.name)
            conflictMode.value = try {
                ConflictMode.valueOf(savedModeStr!!)
            } catch (e: Exception) {
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
        viewModelScope.launch(Dispatchers.IO) {
            // Release persistable URI permissions for URIs that are no longer selected
            val currentUris = selectedUris.value
            val removedUris = currentUris.filter { !uris.contains(it) }
            val persistedPermissions = try {
                context.contentResolver.persistedUriPermissions.map { it.uri }
            } catch (e: Exception) {
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
                } catch (e: SecurityException) {
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
        viewModelScope.launch(Dispatchers.IO) {
            ensurePdfBoxInitialized()
            val unencryptedNames = mutableListOf<String>()
            val unsupportedNames = mutableListOf<String>()
            var extractedMetadata: PdfMetadata? = null

            for ((uri, fileName) in pdfPairs) {
                if (extractedMetadata == null) {
                    var sizeMb = 0.0
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                            sizeMb = fd.statSize.toDouble() / (1024 * 1024)
                        }
                    } catch (e: Exception) {}
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val doc = PDDocument.load(inputStream.buffered(), com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))
                            val info = doc.documentInformation
                            val title = info?.title ?: "Unknown"
                            val author = info?.author ?: "Unknown"
                            val pages = doc.numberOfPages
                            val enc = doc.encryption
                            val encMethod = if (enc != null) "${enc.filter} ${enc.length}-bit" else "None"
                            val perm = doc.currentAccessPermission
                            val canPrint = perm?.canPrint() ?: true
                            val canCopy = perm?.canExtractContent() ?: true
                            extractedMetadata = PdfMetadata(title, author, pages, sizeMb, encMethod, canPrint, canCopy)
                            doc.close()
                        }
                    } catch (e: Exception) {
                        extractedMetadata = PdfMetadata("Unknown (Encrypted)", "Unknown (Encrypted)", 0, sizeMb, "Encrypted", false, false)
                    }
                }
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val doc = try { PDDocument.load(inputStream.buffered(), com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)) } catch (e: Exception) { null }
                        doc?.use {
                            if (!it.isEncrypted) {
                                unencryptedNames.add(fileName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message?.lowercase() ?: ""
                    if (msg.contains("security handler") || msg.contains("certificate") || msg.contains("cryptfilter") || msg.contains("public key")) {
                        unsupportedNames.add(fileName)
                    }
                }
            }

            selectedMetadata.value = extractedMetadata

            if (unencryptedNames.size == pdfPairs.size) {
                statusMessage.value = context.getString(R.string.msg_notice_all_unencrypted)
            } else if (unencryptedNames.isNotEmpty()) {
                statusMessage.value = context.getString(R.string.msg_notice_some_unencrypted, unencryptedNames.joinToString(", "))
            } else if (unsupportedNames.isNotEmpty()) {
                statusMessage.value = context.getString(R.string.msg_warning_unsupported, unsupportedNames.joinToString(", "))
            }
        }
    }

    fun savePassword(name: String, passwordValue: String) {
        viewModelScope.launch {
            repository.insert(PasswordEntity(name = name, passwordValue = passwordValue))
        }
    }

    fun deletePassword(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun restorePassword(password: PasswordEntity) {
        viewModelScope.launch {
            repository.insert(password)
        }
    }

    fun startAutoUnlockFlow(context: Context, uri: Uri, onViewerReady: (Uri) -> Unit) {
        viewModelScope.launch {
            isAutoUnlocking.value = true
            autoUnlockTargetUri.value = uri
            autoUnlockErrorMessage.value = null
            val fileName = FileUtils.getFileName(context, uri)
            autoUnlockFileName.value = fileName

            ensurePdfBoxInitialized()

            var isEncrypted = true
            withContext(Dispatchers.IO) {
                try {
                    openSafeInputStream(context, uri)?.use { input ->
                        try {
                            val doc = PDDocument.load(input.buffered(), com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))
                            if (doc != null) {
                                isEncrypted = doc.isEncrypted
                                doc.close()
                            }
                        } catch (e: InvalidPasswordException) {
                            isEncrypted = true
                        } catch (e: Exception) {
                            val msg = e.message?.lowercase() ?: ""
                            isEncrypted = msg.contains("password") || msg.contains("encrypted") || msg.contains("security") || !msg.contains("syntax")
                        }
                    }
                } catch (e: Exception) {
                    isEncrypted = true
                }
            }

            if (!isEncrypted) {
                // Not encrypted: immediately open in viewer
                isAutoUnlocking.value = false
                showAutoUnlockPasswordPrompt.value = false
                lastDecryptedUri.value = uri
                previewPdfUri.value = uri
                onViewerReady(uri)
                return@launch
            }

            // Encrypted: Try matching with all saved passwords
            val savedPasswordsList = repository.getAllDecryptedPasswords()
            var matchedUri: Uri? = null

            withContext(Dispatchers.IO) {
                for (saved in savedPasswordsList) {
                    var tempFile: java.io.File? = null
                    try {
                        tempFile = java.io.File(context.cacheDir, "auto_decrypted_${System.currentTimeMillis()}.pdf")
                        val status = decryptSinglePdf(context, uri, Uri.fromFile(tempFile), saved.passwordValue)
                        if (status == DecryptStatus.SUCCESS) {
                            matchedUri = Uri.fromFile(tempFile)
                            break
                        } else {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        tempFile?.delete()
                    }
                }
            }

            isAutoUnlocking.value = false
            if (matchedUri != null) {
                lastDecryptedUri.value = matchedUri
                previewPdfUri.value = matchedUri
                showAutoUnlockPasswordPrompt.value = false
                statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                onViewerReady(matchedUri!!)
            } else {
                // No saved password matched, prompt user
                showAutoUnlockPasswordPrompt.value = true
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
            isProcessing.value = true
            autoUnlockErrorMessage.value = null
            var resultUri: Uri? = null
            var resultStatus: DecryptStatus = DecryptStatus.ERROR

            withContext(Dispatchers.IO) {
                var tempFile: java.io.File? = null
                try {
                    tempFile = java.io.File(context.cacheDir, "auto_decrypted_${System.currentTimeMillis()}.pdf")
                    val status = decryptSinglePdf(context, uri, Uri.fromFile(tempFile), enteredPassword)
                    resultStatus = status
                    if (status == DecryptStatus.SUCCESS) {
                        resultUri = Uri.fromFile(tempFile)
                        if (rememberPassword && enteredPassword.isNotBlank()) {
                            val name = autoUnlockFileName.value.ifBlank { "PDF Password" }
                            repository.insert(PasswordEntity(name = name, passwordValue = enteredPassword))
                        }
                    } else {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    tempFile?.delete()
                }
            }

            isProcessing.value = false
            when (resultStatus) {
                DecryptStatus.SUCCESS -> {
                    if (resultUri != null) {
                        lastDecryptedUri.value = resultUri
                        previewPdfUri.value = resultUri
                        showAutoUnlockPasswordPrompt.value = false
                        autoUnlockErrorMessage.value = null
                        statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                        onViewerReady(resultUri!!)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = if (sourceUri.scheme == "file" && sourceUri.path != null) {
                    java.io.FileInputStream(java.io.File(sourceUri.path!!))
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }
                inputStream?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun decryptAndOverwrite(context: Context, inputUri: Uri?, passwordValue: String) {
        if (inputUri == null) return
        decryptAndOverwrite(context, listOf(inputUri), passwordValue)
    }

    fun decryptAndOverwrite(context: Context, inputUris: List<Uri>, passwordValue: String) {
        if (inputUris.isEmpty()) return
        batchJob = viewModelScope.launch {
            isProcessing.value = true
            val isBatch = inputUris.size > 1
            if (isBatch) {
                batchState.value = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
                statusMessage.value = context.getString(R.string.msg_processing_count, inputUris.size)
            } else {
                statusMessage.value = "Processing..."
            }

            var successCount = 0
            var notEncryptedCount = 0
            var wrongPasswordCount = 0
            var unsupportedCount = 0
            var errorCount = 0
            var cancelledCount = 0
            var lastUri: Uri? = null

            try {
                withContext(Dispatchers.IO) {
                    for (inputUri in inputUris) {
                        ensureActive()
                        var tempFile: java.io.File? = null
                        try {
                            tempFile = java.io.File(context.cacheDir, "temp_decrypted_${System.currentTimeMillis()}.pdf")
                            val status = decryptSinglePdf(context, inputUri, android.net.Uri.fromFile(tempFile), passwordValue)

                            when (status) {
                                DecryptStatus.SUCCESS -> {
                                    context.contentResolver.openOutputStream(inputUri, "rwt")?.buffered()?.use { outputStream ->
                                        java.io.FileInputStream(tempFile).buffered().use { inputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                    tempFile.delete()
                                    successCount++
                                    lastUri = inputUri
                                }
                                DecryptStatus.NOT_ENCRYPTED -> {
                                    tempFile.delete()
                                    notEncryptedCount++
                                }
                                DecryptStatus.WRONG_PASSWORD -> {
                                    tempFile.delete()
                                    wrongPasswordCount++
                                }
                                DecryptStatus.UNSUPPORTED_ENCRYPTION -> {
                                    tempFile.delete()
                                    unsupportedCount++
                                }
                                DecryptStatus.ERROR -> {
                                    tempFile.delete()
                                    errorCount++
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                tempFile?.delete()
                                throw e
                            }
                            e.printStackTrace()
                            errorCount++
                        }
                        if (isBatch) {
                            batchState.value = batchState.value.copy(progress = batchState.value.progress + 1)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledCount = inputUris.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
            } finally {
                if (isBatch) {
                    val summaryList = mutableListOf<String>()
                    if (successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, successCount))
                    if (notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, notEncryptedCount))
                    if (wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, wrongPasswordCount))
                    if (unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, unsupportedCount))
                    if (errorCount > 0) summaryList.add(context.getString(R.string.summary_error, errorCount))
                    if (cancelledCount > 0) summaryList.add("Cancelled: $cancelledCount files")

                    statusMessage.value = summaryList.joinToString("\n")
                } else {
                    if (cancelledCount > 0) {
                        statusMessage.value = "Cancelled"
                    } else if (successCount > 0) {
                        statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                    } else if (notEncryptedCount > 0) {
                        statusMessage.value = context.getString(R.string.summary_not_encrypted, 1)
                    } else if (wrongPasswordCount > 0) {
                        statusMessage.value = context.getString(R.string.summary_wrong_password, 1)
                    } else if (unsupportedCount > 0) {
                        statusMessage.value = context.getString(R.string.summary_unsupported, 1)
                    } else if (errorCount > 0) {
                        statusMessage.value = context.getString(R.string.summary_error, 1)
                    }
                }
                if (lastUri != null) {
                    lastDecryptedUri.value = lastUri
                }
                isProcessing.value = false
                batchState.value = BatchState()
            }
        }
    }

    fun decryptAndSaveAs(context: Context, inputUri: Uri?, destUri: Uri?, passwordValue: String) {
        if (inputUri == null || destUri == null) return
        viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Processing..."
            withContext(Dispatchers.IO) {
                try {
                    val status = decryptSinglePdf(context, inputUri, destUri, passwordValue)
                    when (status) {
                        DecryptStatus.SUCCESS -> {
                            statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                            lastDecryptedUri.value = destUri
                        }
                        DecryptStatus.NOT_ENCRYPTED -> {
                            statusMessage.value = context.getString(R.string.summary_not_encrypted, 1)
                        }
                        DecryptStatus.WRONG_PASSWORD -> {
                            statusMessage.value = context.getString(R.string.summary_wrong_password, 1)
                        }
                        DecryptStatus.UNSUPPORTED_ENCRYPTION -> {
                            statusMessage.value = context.getString(R.string.summary_unsupported, 1)
                        }
                        DecryptStatus.ERROR -> {
                            statusMessage.value = context.getString(R.string.summary_error, 1)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    statusMessage.value = context.getString(R.string.summary_error, 1)
                }
            }
            isProcessing.value = false
        }
    }

    fun decryptMultiplePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputDirectoryUri: Uri,
        passwordValue: String,
        prefix: String,
        deleteOriginal: Boolean,
        mode: ConflictMode = conflictMode.value
    ) {
        batchJob = viewModelScope.launch {
            isProcessing.value = true
            batchState.value = BatchState(isProcessing = true, progress = 0, total = inputUris.size)
            statusMessage.value = context.getString(R.string.msg_processing_count, inputUris.size)

            var successCount = 0
            var notEncryptedCount = 0
            var wrongPasswordCount = 0
            var unsupportedCount = 0
            var errorCount = 0
            var cancelledCount = 0
            var lastUri: Uri? = null

            try {
                withContext(Dispatchers.IO) {
                    val documentTree = DocumentFile.fromTreeUri(context, outputDirectoryUri)
                    if (documentTree == null) {
                        statusMessage.value = context.getString(R.string.msg_error_output_dir)
                        isProcessing.value = false
                        batchState.value = BatchState()
                        return@withContext
                    }

                    for (uri in inputUris) {
                        ensureActive()
                        var outputFile: DocumentFile? = null
                        try {
                            val rawFileName = getFileName(context, uri) ?: "decrypted.pdf"
                            val targetFileName = if (prefix.isNotBlank()) {
                                "${prefix}_$rawFileName"
                            } else {
                                rawFileName
                            }

                            val existingFile = documentTree.findFile(targetFileName)
                            var isTempOverwrite = false

                            if (existingFile != null) {
                                if (mode == ConflictMode.SAVE_AS_COPY) {
                                    val uniqueName = getUniqueFileName(documentTree, targetFileName)
                                    outputFile = documentTree.createFile("application/pdf", uniqueName)
                                } else {
                                    // OVERWRITE: create temp file first to avoid overwriting before password verification succeeds
                                    val tempName = "_temp_decrypted_${System.currentTimeMillis()}.pdf"
                                    outputFile = documentTree.createFile("application/pdf", tempName)
                                    isTempOverwrite = true
                                }
                            } else {
                                outputFile = documentTree.createFile("application/pdf", targetFileName)
                            }

                            if (outputFile != null) {
                                val status = decryptSinglePdf(context, uri, outputFile.uri, passwordValue)
                                when (status) {
                                    DecryptStatus.SUCCESS -> {
                                        successCount++
                                        lastUri = outputFile.uri
                                        if (isTempOverwrite && existingFile != null) {
                                            existingFile.delete()
                                            outputFile.renameTo(targetFileName)
                                        }
                                        if (deleteOriginal) {
                                            deleteOriginalFile(context, uri)
                                        }
                                    }
                                    DecryptStatus.NOT_ENCRYPTED -> {
                                        notEncryptedCount++
                                        outputFile.delete()
                                    }
                                    DecryptStatus.WRONG_PASSWORD -> {
                                        wrongPasswordCount++
                                        outputFile.delete()
                                    }
                                    DecryptStatus.UNSUPPORTED_ENCRYPTION -> {
                                        unsupportedCount++
                                        outputFile.delete()
                                    }
                                    DecryptStatus.ERROR -> {
                                        errorCount++
                                        outputFile.delete()
                                    }
                                }
                            } else {
                                errorCount++
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                outputFile?.delete()
                                throw e
                            }
                            e.printStackTrace()
                            errorCount++
                        }
                        batchState.value = batchState.value.copy(progress = batchState.value.progress + 1)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledCount = inputUris.size - (successCount + notEncryptedCount + wrongPasswordCount + unsupportedCount + errorCount)
            } finally {
                if (statusMessage.value == context.getString(R.string.msg_error_output_dir)) {
                    isProcessing.value = false
                    batchState.value = BatchState()
                    return@launch
                }

                val summaryList = mutableListOf<String>()
                if (successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, successCount))
                if (notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, notEncryptedCount))
                if (wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, wrongPasswordCount))
                if (unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, unsupportedCount))
                if (errorCount > 0) summaryList.add(context.getString(R.string.summary_error, errorCount))
                if (cancelledCount > 0) summaryList.add("Cancelled: $cancelledCount files")

                statusMessage.value = summaryList.joinToString("\n")
                if (lastUri != null) {
                    lastDecryptedUri.value = lastUri
                }
                isProcessing.value = false
                batchState.value = BatchState()
            }
        }
    }

    private fun getUniqueFileName(documentTree: DocumentFile, targetFileName: String): String {
        if (documentTree.findFile(targetFileName) == null) {
            return targetFileName
        }
        val baseName = if (targetFileName.contains(".")) {
            targetFileName.substringBeforeLast(".")
        } else {
            targetFileName
        }
        val ext = if (targetFileName.contains(".")) {
            "." + targetFileName.substringAfterLast(".")
        } else {
            ""
        }

        var index = 1
        while (true) {
            val candidate = "$baseName ($index)$ext"
            if (documentTree.findFile(candidate) == null) {
                return candidate
            }
            index++
        }
    }

    private fun openSafeInputStream(context: Context, uri: Uri): java.io.InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    java.io.FileInputStream(java.io.File(uri.path!!))
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun openSafeOutputStream(context: Context, uri: Uri): java.io.OutputStream? {
        return try {
            if (uri.scheme == "file" && uri.path != null) {
                java.io.FileOutputStream(java.io.File(uri.path!!))
            } else {
                context.contentResolver.openOutputStream(uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    internal suspend fun decryptSinglePdf(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): DecryptStatus {
        ensurePdfBoxInitialized()
        try {
            openSafeInputStream(context, inputUri)?.use { inputStream ->
                val docWithoutPass = try { PDDocument.load(inputStream.buffered(), com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)) } catch (e: Exception) { null }
                docWithoutPass?.use {
                    val isEncrypted = it.isEncrypted
                    if (!isEncrypted) {
                        return DecryptStatus.NOT_ENCRYPTED
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore pre-check exception
        }

        try {
            openSafeInputStream(context, inputUri)?.use { inputStream ->
                val document = PDDocument.load(inputStream.buffered(), passwordValue, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))
                try {
                    if (!document.isEncrypted) {
                        return DecryptStatus.NOT_ENCRYPTED
                    }
                    document.setAllSecurityToBeRemoved(true)
                    openSafeOutputStream(context, outputUri)?.buffered()?.use { outputStream ->
                        document.save(outputStream)
                        return DecryptStatus.SUCCESS
                    }
                } finally {
                    document.close()
                }
            }
        } catch (e: InvalidPasswordException) {
            return DecryptStatus.WRONG_PASSWORD
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            return if (msg.contains("password") || msg.contains("incorrect password") || msg.contains("password is required")) {
                DecryptStatus.WRONG_PASSWORD
            } else if (msg.contains("security handler") || msg.contains("cryptfilter") || msg.contains("certificate") || msg.contains("public key") || msg.contains("unsupported")) {
                DecryptStatus.UNSUPPORTED_ENCRYPTION
            } else {
                DecryptStatus.ERROR
            }
        }
        return DecryptStatus.ERROR
    }

    private fun deleteOriginalFile(context: Context, uri: Uri): Boolean {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
