package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FlattenPdfAnnotationsUseCase(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun flattenForms(
        inputUriString: String,
        outputUriString: String,
        passwordValue: String = ""
    ): Boolean = withContext(ioDispatcher) {
        var document: PDDocument? = null
        try {
            val inputStream = pdfRepository.openSafeInputStream(inputUriString)
                ?: return@withContext false

            document = inputStream.use {
                if (passwordValue.isNotEmpty()) {
                    PDDocument.load(it, passwordValue)
                } else {
                    PDDocument.load(it)
                }
            }

            val acroForm = document?.documentCatalog?.acroForm
            acroForm?.flatten()

            val outputStream = pdfRepository.openSafeOutputStream(outputUriString)
                ?: return@withContext false

            outputStream.use { out ->
                document?.save(out)
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
