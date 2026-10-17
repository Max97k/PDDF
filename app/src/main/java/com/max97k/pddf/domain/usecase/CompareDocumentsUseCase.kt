package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.model.ComparisonResult
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CompareDocumentsUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun compare(
        uri1: String,
        uri2: String,
        passwordValue1: String = "",
        passwordValue2: String = ""
    ): ComparisonResult? = withContext(ioDispatcher) {
        val meta1 = pdfRepository.extractMetadata(uri1, passwordValue1)
        val meta2 = pdfRepository.extractMetadata(uri2, passwordValue2)
        if (meta1 == null || meta2 == null) {
            return@withContext null
        }
        ComparisonResult(meta1, meta2)
    }
}
