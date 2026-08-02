package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "pdf-decryptor-db"
    ).build()

    private val repository = PasswordRepository(db.passwordDao())

    val savedPasswords: StateFlow<List<PasswordEntity>> = repository.allPasswords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isProcessing = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)

    init {
        PDFBoxResourceLoader.init(application)
    }

    fun savePassword(name: String, passwordValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(PasswordEntity(name = name, passwordValue = passwordValue))
        }
    }

    fun deletePassword(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }

    fun decryptMultiplePdfs(
        context: Context,
        inputUris: List<Uri>,
        outputDirectoryUri: Uri,
        passwordValue: String,
        prefix: String
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            statusMessage.value = "Decrypting ${inputUris.size} files..."

            var successCount = 0
            var failCount = 0

            withContext(Dispatchers.IO) {
                val documentTree = DocumentFile.fromTreeUri(context, outputDirectoryUri)
                if (documentTree == null) {
                    statusMessage.value = "Error: Cannot access output directory."
                    isProcessing.value = false
                    return@withContext
                }

                for (uri in inputUris) {
                    try {
                        val originalFileName = getFileName(context, uri) ?: "unknown"
                        val newFileName = "${prefix}_$originalFileName"
                        
                        val newFile = documentTree.createFile("application/pdf", newFileName)
                        if (newFile != null) {
                            val success = decryptSinglePdf(context, uri, newFile.uri, passwordValue)
                            if (success) {
                                successCount++
                            } else {
                                failCount++
                                newFile.delete() // clean up empty file if failed
                            }
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failCount++
                    }
                }
            }

            statusMessage.value = "Completed. Success: $successCount, Failed: $failCount"
            isProcessing.value = false
        }
    }

    private fun decryptSinglePdf(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): Boolean {
        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
            val document = PDDocument.load(inputStream, passwordValue)
            try {
                if (document.isEncrypted) {
                    document.setAllSecurityToBeRemoved(true)
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        document.save(outputStream)
                        return true
                    }
                }
            } catch (e: Exception) {
                return false
            } finally {
                document.close()
            }
        }
        return false
    }
}
