package com.max97k.pddf

import androidx.compose.runtime.Immutable

@Immutable
data class PdfFontInfo(
    val name: String,
    val subType: String,
    val isEmbedded: Boolean,
    val isSubset: Boolean
)

@Immutable
data class PdfMetadata(
    val title: String,
    val author: String,
    val pageCount: Int,
    val fileSizeMb: Double,
    val encryptionMethod: String,
    val canPrint: Boolean,
    val canCopy: Boolean,
    val fonts: List<PdfFontInfo> = emptyList(),
    val hasMissingNonEmbeddedSystemFonts: Boolean = false
) {
    val isEncrypted: Boolean
        get() = encryptionMethod.isNotBlank() && encryptionMethod != "None"
}
