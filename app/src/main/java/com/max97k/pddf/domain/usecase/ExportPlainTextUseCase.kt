package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import javax.inject.Inject

class ExportPlainTextUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend operator fun invoke(
        sourceUriString: String,
        destUriString: String,
        isMarkdown: Boolean = false
    ): Boolean = withContext(ioDispatcher) {
        val inputStream = pdfRepository.openSafeInputStream(sourceUriString) ?: return@withContext false
        val outputStream = pdfRepository.openSafeOutputStream(destUriString) ?: run {
            inputStream.close()
            return@withContext false
        }

        try {
            PDDocument.load(inputStream).use { document ->
                val stripper = PDFTextStripper()
                stripper.lineSeparator = "\n"
                stripper.paragraphStart = if (isMarkdown) "\n\n" else "\n"
                stripper.paragraphEnd = "\n"
                stripper.sortByPosition = true

                val text = stripper.getText(document)

                BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.write(text)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }
}
