package com.example.domain.model

import android.net.Uri
import com.example.ConflictMode
import com.example.PdfMetadata

sealed interface PdfUiState {
    data object Idle : PdfUiState

    data class Selected(
        val uris: List<Uri>,
        val fileNames: List<String>,
        val metadata: PdfMetadata? = null,
        val conflictMode: ConflictMode = ConflictMode.SAVE_AS_COPY,
        val rememberConflictChoice: Boolean = false
    ) : PdfUiState

    data class Processing(
        val progress: Int = 0,
        val total: Int = 0,
        val currentFileName: String? = null,
        val isBatch: Boolean = false
    ) : PdfUiState

    data class Success(
        val message: String,
        val decryptedUris: List<Uri> = emptyList(),
        val lastDecryptedUri: Uri? = null
    ) : PdfUiState

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : PdfUiState
}
