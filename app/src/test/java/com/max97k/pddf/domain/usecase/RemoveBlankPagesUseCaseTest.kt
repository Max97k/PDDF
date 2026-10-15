package com.max97k.pddf.domain.usecase

import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.test.core.app.ApplicationProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoveBlankPagesUseCaseTest {

    private lateinit var mockPdfRepository: DomainUseCasesTest.FakePdfRepository

    private lateinit var useCase: RemoveBlankPagesUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        mockPdfRepository = DomainUseCasesTest.FakePdfRepository()
        useCase = RemoveBlankPagesUseCase(mockPdfRepository, testDispatcher)
    }

    @Test
    fun `analyzeBlankPages identifies fully empty pages`() = runTest(testDispatcher) {
        val document = PDDocument()

        // Page 1: Has text
        val page1 = PDPage()
        document.addPage(page1)
        var contentStream = PDPageContentStream(document, page1)
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA, 12f)
        contentStream.newLineAtOffset(100f, 700f)
        contentStream.showText("Hello")
        contentStream.endText()
        contentStream.close()

        // Page 2: Blank
        val page2 = PDPage()
        document.addPage(page2)

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()

        val inputStream: InputStream = ByteArrayInputStream(baos.toByteArray())
        mockPdfRepository.configureMockInputStream(inputStream)

        val result = useCase.analyzeBlankPages("test_uri")

        assertEquals(0, result.size)

    }

    @Test
    fun `removeBlankPages correctly removes specified pages and exports`() = runTest(testDispatcher) {
        val document = PDDocument()

        // Add 3 pages
        document.addPage(PDPage()) // Page 1
        document.addPage(PDPage()) // Page 2
        document.addPage(PDPage()) // Page 3

        val baos = ByteArrayOutputStream()
        document.save(baos)
        document.close()

        val inputStream: InputStream = ByteArrayInputStream(baos.toByteArray())
        mockPdfRepository.configureMockInputStream(inputStream)

        val outBaos = ByteArrayOutputStream()
        mockPdfRepository.configureMockOutputStream(outBaos)

        val result = useCase.removeBlankPages("source_uri", "dest_uri", listOf(2)) // Remove page 2

        assertTrue(result)

        // Check output
        val outDoc = PDDocument.load(ByteArrayInputStream(outBaos.toByteArray()))
        assertEquals(2, outDoc.numberOfPages) // Only 2 pages should remain
        outDoc.close()
    }
}
