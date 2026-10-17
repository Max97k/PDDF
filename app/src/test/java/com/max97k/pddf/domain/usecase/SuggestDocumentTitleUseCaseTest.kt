package com.max97k.pddf.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.PdfMetadata
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SuggestDocumentTitleUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun invoke_whenMetadataHasValidTitle_returnsSanitizedMetadataTitle() = runTest(testDispatcher) {
        val fakeRepo = DomainUseCasesTest.FakePdfRepository(
            metadataResult = PdfMetadata(
                title = "2026/10 Bank Statement: Account *1234*",
                author = "Bank",
                pageCount = 2,
                fileSizeMb = 0.5,
                pdfVersion = "1.7",
                encryptionMethod = "None",
                canPrint = true,
                canCopy = true
            )
        )
        val useCase = SuggestDocumentTitleUseCase(fakeRepo, testDispatcher)

        val suggested = useCase("file:///statement.pdf")
        assertEquals("2026_10 Bank Statement_ Account _1234", suggested)
    }

    @Test
    fun invoke_whenMetadataTitleIsUnknown_fallsBackToFirstPageHeaderLine() = runTest(testDispatcher) {
        val baos = ByteArrayOutputStream()
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                cs.newLineAtOffset(72f, 720f)
                cs.showText("Annual Financial Summary <2026>")
                cs.endText()
            }
            doc.save(baos)
        }
        val pdfBytes = baos.toByteArray()

        val fakeRepo = object : DomainUseCasesTest.FakePdfRepository(
            metadataResult = PdfMetadata(
                title = "Unknown",
                author = "Unknown",
                pageCount = 1,
                fileSizeMb = 0.1,
                pdfVersion = "1.4",
                encryptionMethod = "None",
                canPrint = true,
                canCopy = true
            )
        ) {
            override fun openSafeInputStream(uriString: String): InputStream {
                return ByteArrayInputStream(pdfBytes)
            }
        }

        val useCase = SuggestDocumentTitleUseCase(fakeRepo, testDispatcher)
        val suggested = useCase("file:///summary.pdf")
        assertEquals("Annual Financial Summary _2026", suggested)
    }

    @Test
    fun invoke_whenMetadataAndPageTextAreEmpty_returnsNull() = runTest(testDispatcher) {
        val baos = ByteArrayOutputStream()
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(baos)
        }
        val pdfBytes = baos.toByteArray()

        val fakeRepo = object : DomainUseCasesTest.FakePdfRepository(metadataResult = null) {
            override fun openSafeInputStream(uriString: String): InputStream {
                return ByteArrayInputStream(pdfBytes)
            }
        }

        val useCase = SuggestDocumentTitleUseCase(fakeRepo, testDispatcher)
        val suggested = useCase("file:///blank.pdf")
        assertNull(suggested)
    }
}
