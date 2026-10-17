package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Suggests a sanitized, human-readable filename based on PDF metadata title
 * or the first non-blank line on page 1.
 */
class SuggestDocumentTitleUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val MAX_FILENAME_LENGTH = 100
        private val INVALID_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\r\\n\\t]+")

        fun sanitizeFileName(raw: String): String {
            val cleaned = raw
                .trim()
                .replace(INVALID_FILENAME_CHARS, "_")
                .replace(Regex("\\s+"), " ")
                .trim('.', ' ', '_')
            return if (cleaned.length > MAX_FILENAME_LENGTH) {
                cleaned.take(MAX_FILENAME_LENGTH).trim('.', ' ', '_')
            } else {
                cleaned
            }
        }
    }

    suspend operator fun invoke(
        sourceUri: String,
        passwordValue: String = ""
    ): String? = withContext(ioDispatcher) {
        try {
            val metadata = pdfRepository.extractMetadata(sourceUri, passwordValue)
            val metaTitle = metadata?.title?.trim()
            if (!metaTitle.isNullOrBlank() && !metaTitle.equals("Unknown", ignoreCase = true)) {
                val sanitized = sanitizeFileName(metaTitle)
                if (sanitized.isNotBlank()) {
                    return@withContext sanitized
                }
            }

            pdfRepository.openSafeInputStream(sourceUri)?.use { inputStream ->
                PDDocument.load(
                    inputStream.buffered(),
                    passwordValue,
                    MemoryUsageSetting.setupMixed(50L * 1024L * 1024L)
                ).use { document ->
                    if (document.numberOfPages > 0) {
                        val stripper = PDFTextStripper().apply {
                            startPage = 1
                            endPage = 1
                        }
                        val firstPageText = stripper.getText(document)
                        val firstLine = firstPageText
                            .lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotBlank() }

                        if (!firstLine.isNullOrBlank()) {
                            val sanitized = sanitizeFileName(firstLine)
                            if (sanitized.isNotBlank()) {
                                return@withContext sanitized
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
