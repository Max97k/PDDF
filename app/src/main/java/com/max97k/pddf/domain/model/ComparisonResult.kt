package com.max97k.pddf.domain.model

import com.max97k.pddf.PdfMetadata

data class ComparisonResult(
    val meta1: PdfMetadata,
    val meta2: PdfMetadata
) {
    val pageCountDiffers: Boolean get() = meta1.pageCount != meta2.pageCount
    val fileSizeDiffers: Boolean get() = Math.abs(meta1.fileSizeMb - meta2.fileSizeMb) > 0.01
    val versionDiffers: Boolean get() = meta1.version != meta2.version
    val encryptionDiffers: Boolean get() = meta1.encryptionMethod != meta2.encryptionMethod
    val permissionsDiffer: Boolean get() = meta1.canPrint != meta2.canPrint || meta1.canCopy != meta2.canCopy
}
