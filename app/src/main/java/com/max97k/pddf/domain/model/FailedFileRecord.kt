package com.max97k.pddf.domain.model

import com.max97k.pddf.DecryptStatus

data class FailedFileRecord(
    val uri: String,
    val fileName: String,
    val errorType: DecryptStatus,
    val message: String
)
