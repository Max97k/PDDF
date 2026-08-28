package com.example

data class PdfMetadata(
    val title: String,
    val author: String,
    val pageCount: Int,
    val fileSizeMb: Double,
    val encryptionMethod: String,
    val canPrint: Boolean,
    val canCopy: Boolean
)
