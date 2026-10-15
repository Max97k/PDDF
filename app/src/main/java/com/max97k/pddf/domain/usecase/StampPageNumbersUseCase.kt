package com.max97k.pddf.domain.usecase

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StampPageNumbersUseCase(
    private val pdfRepository: PdfRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun stamp(
        sourceUri: String,
        destUri: String,
        format: String,
        position: String,
        fontSize: Float,
        margin: Float,
        passwordValue: String = ""
    ): Boolean = withContext(ioDispatcher) {
        val inputStream = pdfRepository.openSafeInputStream(sourceUri) ?: return@withContext false
        val outputStream = pdfRepository.openSafeOutputStream(destUri) ?: return@withContext false

        var success = false
        inputStream.use { input ->
            outputStream.use { output ->
                PDDocument.load(input, passwordValue).use { document ->
                    val font = PDType1Font.HELVETICA
                    val totalPages = document.numberOfPages

                    for (i in 0 until totalPages) {
                        val page = document.getPage(i)
                        val pageNum = i + 1

                        val formattedText = try {
                            String.format(format, pageNum, totalPages)
                        } catch (e: Exception) {
                            format.replace("%d", pageNum.toString())
                        }

                        val textWidth = font.getStringWidth(formattedText) / 1000f * fontSize
                        val mediaBox = page.mediaBox

                        val rotation = page.rotation
                        val isLandscape = rotation == 90 || rotation == 270
                        val width = if (isLandscape) mediaBox.height else mediaBox.width

                        val x: Float
                        val y: Float

                        if (position.equals("bottom-right", ignoreCase = true)) {
                            x = width - textWidth - margin
                            y = margin
                        } else {
                            // bottom-center
                            x = (width - textWidth) / 2f
                            y = margin
                        }

                        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                            contentStream.beginText()
                            contentStream.setFont(font, fontSize)

                            when (rotation) {
                                90 -> contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(90.0), mediaBox.width, 0f).apply { translate(y, -x) })
                                180 -> contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(180.0), mediaBox.width, mediaBox.height).apply { translate(-x, -y) })
                                270 -> contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(270.0), 0f, mediaBox.height).apply { translate(-y, x) })
                                else -> contentStream.newLineAtOffset(x + mediaBox.lowerLeftX, y + mediaBox.lowerLeftY)
                            }

                            contentStream.showText(formattedText)
                            contentStream.endText()
                        }
                    }
                    document.save(output)
                    success = true
                }
            }
        }
        success
    }
}
