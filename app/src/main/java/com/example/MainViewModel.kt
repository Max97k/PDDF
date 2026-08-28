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
import com.example.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        ).build().passwordDao()
    )
) : AndroidViewModel(application) {

    val savedPasswords: StateFlow<List<PasswordEntity>> = repository.allPasswords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedFileNames = MutableStateFlow<List<String>>(emptyList())
    val isProcessing = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    val lastDecryptedUri = MutableStateFlow<Uri?>(null)

    val conflictMode = MutableStateFlow(ConflictMode.SAVE_AS_COPY)
    val rememberConflictChoice = MutableStateFlow(false)

    private val prefs = application.getSharedPreferences("pdf_decryptor_prefs", Context.MODE_PRIVATE)

    init {
        PDFBoxResourceLoader.init(application)
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
            val pdfPairs = uris.mapNotNull { uri ->
                val name = FileUtils.getFileName(context, uri)
                val isPdf = name.endsWith(".pdf", ignoreCase = true) ||
                        context.contentResolver.getType(uri)?.contains("pdf", ignoreCase = true) == true
                if (isPdf) Pair(uri, name) else null
            }

            val pdfUris = pdfPairs.map { it.first }
            val pdfNames = pdfPairs.map { it.second }

            selectedUris.value = pdfUris
            selectedFileNames.value = pdfNames
            statusMessage.value = null

            if (pdfUris.isNotEmpty()) {
                checkSelectedPdfs(context, pdfPairs)
            }
        }
    }

    private fun checkSelectedPdfs(context: Context, pdfPairs: List<Pair<Uri, String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val unencryptedNames = mutableListOf<String>()
            val unsupportedNames = mutableListOf<String>()

            for ((uri, fileName) in pdfPairs) {
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

    fun decryptAndOverwrite(context: Context, inputUri: Uri?, passwordValue: String) {
        if (inputUri == null) return
        viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Processing..."
            withContext(Dispatchers.IO) {
                try {
                    // Create a temporary file in cache to store decrypted content
                    val tempFile = java.io.File(context.cacheDir, "temp_decrypted_${System.currentTimeMillis()}.pdf")
                    try {
                        val status = decryptSinglePdf(context, inputUri, android.net.Uri.fromFile(tempFile), passwordValue)
    
                        when (status) {
                            DecryptStatus.SUCCESS -> {
                                // Write temp file contents back to the original URI using SAF
                                context.contentResolver.openOutputStream(inputUri, "rwt")?.buffered()?.use { outputStream ->
                                    java.io.FileInputStream(tempFile).buffered().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                statusMessage.value = context.getString(R.string.summary_decrypted_saved, 1)
                                lastDecryptedUri.value = inputUri
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
                    } finally {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    statusMessage.value = context.getString(R.string.summary_error, 1)
                }
            }
            isProcessing.value = false
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
        viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = context.getString(R.string.msg_processing_count, inputUris.size)

            var successCount = 0
            var notEncryptedCount = 0
            var wrongPasswordCount = 0
            var unsupportedCount = 0
            var errorCount = 0
            var lastUri: Uri? = null

            withContext(Dispatchers.IO) {
                val documentTree = DocumentFile.fromTreeUri(context, outputDirectoryUri)
                if (documentTree == null) {
                    statusMessage.value = context.getString(R.string.msg_error_output_dir)
                    isProcessing.value = false
                    return@withContext
                }

                for (uri in inputUris) {
                    try {
                        val rawFileName = getFileName(context, uri) ?: "decrypted.pdf"
                        val targetFileName = if (prefix.isNotBlank()) {
                            "${prefix}_$rawFileName"
                        } else {
                            rawFileName
                        }

                        val existingFile = documentTree.findFile(targetFileName)
                        var outputFile: DocumentFile? = null
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
                        e.printStackTrace()
                        errorCount++
                    }
                }
            }

            val summaryList = mutableListOf<String>()
            if (successCount > 0) summaryList.add(context.getString(R.string.summary_decrypted_saved, successCount))
            if (notEncryptedCount > 0) summaryList.add(context.getString(R.string.summary_not_encrypted, notEncryptedCount))
            if (wrongPasswordCount > 0) summaryList.add(context.getString(R.string.summary_wrong_password, wrongPasswordCount))
            if (unsupportedCount > 0) summaryList.add(context.getString(R.string.summary_unsupported, unsupportedCount))
            if (errorCount > 0) summaryList.add(context.getString(R.string.summary_error, errorCount))

            statusMessage.value = summaryList.joinToString("\n")
            if (lastUri != null) {
                lastDecryptedUri.value = lastUri
            }
            isProcessing.value = false
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

    internal fun decryptSinglePdf(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): DecryptStatus {
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
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
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val document = PDDocument.load(inputStream.buffered(), passwordValue, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024))
                try {
                    if (!document.isEncrypted) {
                        return DecryptStatus.NOT_ENCRYPTED
                    }
                    document.setAllSecurityToBeRemoved(true)
                    context.contentResolver.openOutputStream(outputUri)?.buffered()?.use { outputStream ->
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
