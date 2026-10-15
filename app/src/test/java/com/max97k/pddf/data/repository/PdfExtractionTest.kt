package com.max97k.pddf.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.util.PageRangeParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfExtractionTest {

    private lateinit var context: Context
    private lateinit var repository: PdfRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        repository = PdfRepositoryImpl(context)
    }

    @Test
    fun testExtractPagesSuccess() = runTest {
        // Setup: Create a temporary dummy source PDF with 5 pages
        val sourceFile = File(context.cacheDir, "source.pdf")
        PDDocument().use { doc ->
            for (i in 0 until 5) {
                doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            }
            doc.save(sourceFile)
        }

        val destFile = File(context.cacheDir, "dest.pdf")
        val destUriString = "file://${destFile.absolutePath}"
        val sourceUriString = "file://${sourceFile.absolutePath}"

        // Action: Extract pages 1, 3, and 5
        val result = repository.extractPages(sourceUriString, destUriString, "1,3,5")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())

        // Verify the created file has 3 pages
        PDDocument.load(destFile).use { doc ->
            assertEquals(3, doc.numberOfPages)
        }

        // Cleanup
        sourceFile.delete()
        destFile.delete()
    }

    @Test
    fun testExtractPagesInvalidRange() = runTest {
        // Setup: Create a temporary dummy source PDF with 5 pages
        val sourceFile = File(context.cacheDir, "source.pdf")
        PDDocument().use { doc ->
            for (i in 0 until 5) {
                doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            }
            doc.save(sourceFile)
        }

        val destFile = File(context.cacheDir, "dest.pdf")
        val destUriString = "file://${destFile.absolutePath}"
        val sourceUriString = "file://${sourceFile.absolutePath}"

        // Action: Extract invalid range (e.g. out of bounds)
        val result = repository.extractPages(sourceUriString, destUriString, "6")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Invalid or empty page range", result.exceptionOrNull()?.message)

        // Cleanup
        sourceFile.delete()
    }
}
