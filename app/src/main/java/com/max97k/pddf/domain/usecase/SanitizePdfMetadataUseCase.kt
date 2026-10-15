package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class SanitizePdfMetadataUseCase(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    open suspend fun sanitize(
        sourceUriString: String,
        destUriString: String,
        passwordValue: String = ""
    ): Boolean = withContext(ioDispatcher) {
        val inputStream = pdfRepository.openSafeInputStream(sourceUriString) ?: return@withContext false

        try {
            val document = inputStream.use {
                PDDocument.load(
                    it.buffered(),
                    passwordValue,
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024)
                )
            }

            document.use { doc ->
                val info = doc.documentInformation
                if (info != null) {
                    info.title = null
                    info.author = null
                    info.creator = null
                    info.producer = null
                    info.keywords = null
                    info.creationDate = null
                    info.modificationDate = null
                    doc.documentInformation = info
                }

                val outputStream = pdfRepository.openSafeOutputStream(destUriString) ?: return@withContext false
                outputStream.use { out ->
                    doc.save(out.buffered())
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
