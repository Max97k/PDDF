package com.max97k.pddf

import androidx.compose.runtime.Immutable

@Immutable
data class SignatureInfo(
    val signerName: String,
    val signingDate: String,
    val reason: String,
    val location: String,
    val isValid: Boolean,
    val certificateSubject: String,
    val certificateIssuer: String
)
