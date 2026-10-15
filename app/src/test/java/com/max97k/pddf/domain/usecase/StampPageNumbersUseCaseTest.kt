package com.max97k.pddf.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.domain.repository.PdfEncryptionCheck

class FakePdfRepository : PdfRepository {
    var inStream: InputStream? = null
    var outStream: OutputStream? = null

    override fun openSafeInputStream(uriString: String): InputStream? = inStream
    override fun openSafeOutputStream(uriString: String): OutputStream? = outStream

    override suspend fun checkEncryption(sourceUri: String) = PdfEncryptionCheck.NotEncrypted
    override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray) = DecryptStatus.SUCCESS
    override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String) = DecryptStatus.SUCCESS
    override suspend fun extractMetadata(sourceUri: String, passwordValue: String) = null
    override suspend fun copy(sourceUri: String, destUri: String) = false
    override suspend fun createTempFile(prefix: String, suffix: String) = ""
    override suspend fun getShareableUri(fileUri: String) = ""
    override suspend fun deleteFile(filePathOrUri: String) = false
    override suspend fun getFileName(uriString: String) = ""
    override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String) = null
    override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String) = null
    override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String) = false
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StampPageNumbersUseCaseTest {

    private lateinit var pdfRepository: FakePdfRepository
    private lateinit var useCase: StampPageNumbersUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)

        pdfRepository = FakePdfRepository()
        useCase = StampPageNumbersUseCase(pdfRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            doc.save(out)
        }
        return out.toByteArray()
    }

    @Test
    fun stamp_bottomCenter_addsText() = runTest(testDispatcher) {
        val inputPdf = createTestPdf()
        val outStream = ByteArrayOutputStream()

        pdfRepository.inStream = ByteArrayInputStream(inputPdf)
        pdfRepository.outStream = outStream

        val result = useCase.stamp(
            sourceUri = "source",
            destUri = "dest",
            format = "Page %d of %d",
            position = "bottom-center",
            fontSize = 12f,
            margin = 20f
        )

        assertTrue(result)

        PDDocument.load(ByteArrayInputStream(outStream.toByteArray())).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(doc)
            assertTrue(text.contains("Page 1 of 2"))
            assertTrue(text.contains("Page 2 of 2"))
        }
    }

    @Test
    fun stamp_bottomRight_addsText() = runTest(testDispatcher) {
        val inputPdf = createTestPdf()
        val outStream = ByteArrayOutputStream()

        pdfRepository.inStream = ByteArrayInputStream(inputPdf)
        pdfRepository.outStream = outStream

        val result = useCase.stamp(
            sourceUri = "source",
            destUri = "dest",
            format = "%d / %d",
            position = "bottom-right",
            fontSize = 12f,
            margin = 20f
        )

        assertTrue(result)

        PDDocument.load(ByteArrayInputStream(outStream.toByteArray())).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(doc)
            assertTrue(text.contains("1 / 2"))
            assertTrue(text.contains("2 / 2"))
        }
    }
}
