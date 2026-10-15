package com.max97k.pddf.domain.usecase

import android.graphics.Bitmap
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExportPdfPageAsImageUseCase @Inject constructor(
    private val pdfRepository: PdfRepository
) {
    suspend operator fun invoke(
        sourceUri: String,
        pageIndex: Int,
        outputUri: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Boolean = withContext(Dispatchers.IO) {
        pdfRepository.exportPageAsImage(sourceUri, pageIndex, outputUri, format, quality)
    }
}
