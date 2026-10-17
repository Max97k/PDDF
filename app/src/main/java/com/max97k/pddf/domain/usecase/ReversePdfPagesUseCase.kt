package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class ReversePdfPagesUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    open suspend fun reversePages(sourceUriString: String): kotlin.Result<String> = withContext(ioDispatcher) {
        try {
            val inputStream = pdfRepository.openSafeInputStream(sourceUriString)
                ?: return@withContext kotlin.Result.failure(Exception("Cannot open input stream"))

            PDDocument.load(inputStream).use { doc ->
                val pageCount = doc.numberOfPages
                if (pageCount <= 1) {
                    return@withContext kotlin.Result.success(pdfRepository.getShareableUri(sourceUriString))
                }

                PDDocument().use { newDoc ->
                    for (i in pageCount - 1 downTo 0) {
                        newDoc.addPage(doc.getPage(i))
                    }

                    // createTempFile returns absolute path. So we prepend file:// to get a URI string
                    val tempFilePath = pdfRepository.createTempFile("reversed_")
                    val outUri = "file://$tempFilePath"

                    pdfRepository.openSafeOutputStream(outUri)?.use { output ->
                        newDoc.save(output)
                    } ?: return@withContext kotlin.Result.failure(Exception("Cannot open output stream"))

                    kotlin.Result.success(pdfRepository.getShareableUri(outUri))
                }
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
}
