package com.max97k.pddf.domain.usecase

import android.graphics.Bitmap
import com.tom_roush.pdfbox.rendering.PDFRenderer

import com.max97k.pddf.di.IoDispatcher
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RemoveBlankPagesUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun analyzeBlankPages(uriString: String): List<Int> = withContext(ioDispatcher) {
        val blankPages = mutableListOf<Int>()
        try {
            pdfRepository.openSafeInputStream(uriString)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    for (i in 0 until document.numberOfPages) {
                        val pageNum = i + 1
                        stripper.startPage = pageNum
                        stripper.endPage = pageNum
                        val text = stripper.getText(document)

                        if (text.trim().isEmpty()) {
                            val renderer = PDFRenderer(document)
                            try {
                                val bitmap = renderer.renderImageWithDPI(i, 36f)
                                if (isBlankImage(bitmap)) {
                                    blankPages.add(pageNum)
                                }
                                bitmap.recycle()
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        blankPages
    }

    suspend fun removeBlankPages(
        sourceUriString: String,
        destUriString: String,
        pagesToRemove: List<Int>
    ): Boolean = withContext(ioDispatcher) {
        try {
            pdfRepository.openSafeInputStream(sourceUriString)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val sortedPages = pagesToRemove.sortedDescending()
                    for (pageNum in sortedPages) {
                        if (pageNum in 1..document.numberOfPages) {
                            document.removePage(pageNum - 1)
                        }
                    }
                    pdfRepository.openSafeOutputStream(destUriString)?.use { outputStream ->
                        document.save(outputStream)
                        return@withContext true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun isBlankImage(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var nonWhitePixels = 0
        val threshold = 250

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (luminance < threshold) {
                nonWhitePixels++
            }
        }

        return nonWhitePixels < (width * height * 0.001).toInt()
    }
}
