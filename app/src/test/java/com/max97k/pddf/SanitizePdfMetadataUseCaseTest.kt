package com.max97k.pddf

import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.domain.usecase.SanitizePdfMetadataUseCase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SanitizePdfMetadataUseCaseTest {

    private lateinit var useCase: SanitizePdfMetadataUseCase

    @Before
    fun setup() {
        PDFBoxResourceLoader.init(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test
    fun testSanitizeMetadata_RemovesIdentifyingFields() = runBlocking {
        // Create a temporary PDF with metadata
        val sourceFile = File.createTempFile("source", ".pdf")
        sourceFile.deleteOnExit()
        val doc = PDDocument()
        val info = PDDocumentInformation()
        info.title = "Confidential Title"
        info.author = "Secret Author"
        info.creator = "Top Secret Creator"
        info.producer = "Private Producer"
        info.keywords = "Classified, Secret"
        info.creationDate = Calendar.getInstance()
        info.modificationDate = Calendar.getInstance()
        doc.documentInformation = info
        doc.save(sourceFile)
        doc.close()

        val destFile = File.createTempFile("dest", ".pdf")
        destFile.deleteOnExit()

        val sourceUri = "file://${sourceFile.absolutePath}"
        val destUri = "file://${destFile.absolutePath}"

        val mockPdfRepository = object : PdfRepository {
            override fun openSafeInputStream(uriString: String): InputStream? {
                return if (uriString == sourceUri) FileInputStream(sourceFile) else null
            }

            override fun openSafeOutputStream(uriString: String): OutputStream? {
                return if (uriString == destUri) FileOutputStream(destFile) else null
            }

            override suspend fun checkEncryption(sourceUri: String): com.max97k.pddf.domain.repository.PdfEncryptionCheck = TODO()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray): DecryptStatus = TODO()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String): DecryptStatus = TODO()
            override suspend fun extractMetadata(sourceUri: String, passwordValue: String): PdfMetadata? = TODO()
            override suspend fun copy(sourceUri: String, destUri: String): Boolean = TODO()
            override suspend fun createTempFile(prefix: String, suffix: String): String = TODO()
            override suspend fun getShareableUri(fileUri: String): String = TODO()
            override suspend fun deleteFile(filePathOrUri: String): Boolean = TODO()
            override suspend fun getFileName(uriString: String): String = TODO()
            override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String): String? = TODO()
            override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String): String? = TODO()
            override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String): Boolean = TODO()
            override suspend fun exportPageAsImage(sourceUri: String, pageIndex: Int, outputUri: String, format: android.graphics.Bitmap.CompressFormat, quality: Int): Boolean = TODO()
            override suspend fun compressPdf(sourceUri: String, destUri: String): com.max97k.pddf.util.Result<Long> = TODO()
            override suspend fun addWatermark(sourceUri: String, destUri: String, text: String, opacity: Float, fontSize: Float): Boolean = TODO()
            override suspend fun extractPages(sourceUri: String, destUri: String, pageRange: String): kotlin.Result<Int> = TODO()
            override suspend fun packageToZip(fileUris: List<String>, outputZipUri: String, onProgress: (Int, Int) -> Unit): Boolean = TODO()
            override suspend fun rotatePages(sourceUri: String, destUri: String, pageIndices: List<Int>?, rotationDegrees: Int): Boolean = TODO()
        }

        useCase = SanitizePdfMetadataUseCase(mockPdfRepository, Dispatchers.Unconfined)

        // Run use case
        val result = useCase.sanitize(sourceUri, destUri)
        assertTrue("Sanitize should return true on success", result)

        // Verify output file
        val sanitizedDoc = PDDocument.load(destFile)
        val sanitizedInfo = sanitizedDoc.documentInformation

        assertNull("Title should be null", sanitizedInfo.title)
        assertNull("Author should be null", sanitizedInfo.author)
        assertNull("Creator should be null", sanitizedInfo.creator)
        assertNull("Producer should be null", sanitizedInfo.producer)
        assertNull("Keywords should be null", sanitizedInfo.keywords)
        assertNull("Creation date should be null", sanitizedInfo.creationDate)
        assertNull("Modification date should be null", sanitizedInfo.modificationDate)

        sanitizedDoc.close()
    }
}
