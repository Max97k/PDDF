package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

open class DecryptPdfUseCase(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    open suspend fun decrypt(
        inputUriString: String,
        outputUriString: String,
        passwordChars: CharArray
    ): DecryptStatus = withContext(ioDispatcher) {
        pdfRepository.decrypt(inputUriString, outputUriString, passwordChars)
    }

    open suspend fun decrypt(
        inputUriString: String,
        outputUriString: String,
        passwordValue: String
    ): DecryptStatus = withContext(ioDispatcher) {
        pdfRepository.decrypt(inputUriString, outputUriString, passwordValue)
    }

    open suspend fun decryptWithTiming(
        inputUriString: String,
        outputUriString: String,
        passwordValue: String
    ): Pair<DecryptStatus, Long> = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        val status = pdfRepository.decrypt(inputUriString, outputUriString, passwordValue)
        val duration = System.currentTimeMillis() - startTime
        Pair(status, duration)
    }

    open suspend fun extractMetadata(
        uriString: String,
        passwordValue: String = ""
    ): PdfMetadata? = withContext(ioDispatcher) {
        pdfRepository.extractMetadata(uriString, passwordValue)
    }

    open suspend fun copy(
        sourceUriString: String,
        destUriString: String
    ): Boolean = withContext(ioDispatcher) {
        pdfRepository.copy(sourceUriString, destUriString)
    }

    open fun openSafeInputStream(uriString: String): InputStream? {
        return pdfRepository.openSafeInputStream(uriString)
    }

    open fun openSafeOutputStream(uriString: String): OutputStream? {
        return pdfRepository.openSafeOutputStream(uriString)
    }
}
