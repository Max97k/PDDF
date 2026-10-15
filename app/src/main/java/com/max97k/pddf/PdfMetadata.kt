package com.max97k.pddf

import androidx.compose.runtime.Immutable
import com.max97k.pddf.domain.usecase.PdfAttachment

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
    val pdfVersion: String,
    val encryptionMethod: String,
    val canPrint: Boolean,
    val canCopy: Boolean,
    val signatures: List<SignatureInfo> = emptyList(),
    val attachments: List<PdfAttachment> = emptyList(),
    val fonts: List<PdfFontInfo> = emptyList(),
    val hasMissingNonEmbeddedSystemFonts: Boolean = false,
    val version: Float = 1.4f
) {
    val isEncrypted: Boolean
        get() = encryptionMethod.isNotBlank() && encryptionMethod != "None"
}
