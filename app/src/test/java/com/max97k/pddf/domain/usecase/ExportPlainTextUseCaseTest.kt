package com.max97k.pddf.domain.usecase

import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExportPlainTextUseCaseTest {

    private lateinit var useCase: ExportPlainTextUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val inputPdfBytes: ByteArray
        get() {
            val document = PDDocument()
            val page = PDPage()
            document.addPage(page)
            val font = PDType1Font.HELVETICA
            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(font, 12f)
                contentStream.newLineAtOffset(100f, 700f)
                contentStream.showText("This is a test paragraph.")
                contentStream.endText()

                contentStream.beginText()
                contentStream.setFont(font, 12f)
                contentStream.newLineAtOffset(100f, 650f)
                contentStream.showText("This is another test paragraph.")
                contentStream.endText()
            }
            val baos = ByteArrayOutputStream()
            document.save(baos)
            document.close()
            return baos.toByteArray()
        }

    @Before
    fun setup() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())

        val mockRepository = object : PdfRepository {
            override suspend fun checkEncryption(sourceUri: String) = throw NotImplementedError()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray) = throw NotImplementedError()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String) = throw NotImplementedError()
            override suspend fun extractMetadata(sourceUri: String, passwordValue: String) = throw NotImplementedError()
            override suspend fun copy(sourceUri: String, destUri: String) = throw NotImplementedError()
            override suspend fun createTempFile(prefix: String, suffix: String) = throw NotImplementedError()
            override suspend fun getShareableUri(fileUri: String) = throw NotImplementedError()
            override suspend fun deleteFile(filePathOrUri: String) = throw NotImplementedError()
            override suspend fun getFileName(uriString: String) = throw NotImplementedError()
            override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String) = throw NotImplementedError()
            override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String) = throw NotImplementedError()
            override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String) = throw NotImplementedError()

            override fun openSafeInputStream(uriString: String): InputStream? {
                if (uriString == "source") {
                    return ByteArrayInputStream(inputPdfBytes)
                }
                return null
            }

            override fun openSafeOutputStream(uriString: String): OutputStream? {
                if (uriString == "dest") {
                    return testOutputStream
                }
                return null
            }
        }

        useCase = ExportPlainTextUseCase(mockRepository, testDispatcher)
    }

    private val testOutputStream = ByteArrayOutputStream()

    @After
    fun tearDown() {
        testOutputStream.reset()
    }

    @Test
    fun `invoke with valid source and txt should extract plain text`() = runTest(testDispatcher) {
        val result = useCase("source", "dest", isMarkdown = false)
        assertTrue(result)

        val extractedText = testOutputStream.toString("UTF-8")
        assertTrue(extractedText.contains("This is a test paragraph."))
        assertTrue(extractedText.contains("This is another test paragraph."))
        assertTrue(extractedText.contains("\n"))
    }

    @Test
    fun `invoke with valid source and md should extract text with double line break`() = runTest(testDispatcher) {
        val result = useCase("source", "dest", isMarkdown = true)
        assertTrue(result)

        val extractedText = testOutputStream.toString("UTF-8")
        assertTrue(extractedText.contains("This is a test paragraph."))
        assertTrue(extractedText.contains("This is another test paragraph."))
        assertTrue(extractedText.contains("\n\n"))
    }
}
