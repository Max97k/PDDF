package com.max97k.pddf

import androidx.compose.runtime.Immutable

@Immutable
data class PdfMetadata(
    val title: String,
    val author: String,
    val pageCount: Int,
    val fileSizeMb: Double,
    val encryptionMethod: String,
    val canPrint: Boolean,
    val canCopy: Boolean,
    val signatures: List<SignatureInfo> = emptyList()
) {
    val isEncrypted: Boolean
        get() = encryptionMethod.isNotBlank() && encryptionMethod != "None"
}
