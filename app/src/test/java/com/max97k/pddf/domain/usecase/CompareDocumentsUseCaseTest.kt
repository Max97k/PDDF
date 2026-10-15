package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CompareDocumentsUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createFakeRepository(
        metadataMap: Map<String, PdfMetadata>
    ): PdfRepository = object : PdfRepository {
        override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = PdfEncryptionCheck.NotEncrypted
        override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray): DecryptStatus = DecryptStatus.SUCCESS
        override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String): DecryptStatus = DecryptStatus.SUCCESS
        override suspend fun extractMetadata(sourceUri: String, passwordValue: String): PdfMetadata? = metadataMap[sourceUri]
        override suspend fun copy(sourceUri: String, destUri: String): Boolean = true
        override suspend fun createTempFile(prefix: String, suffix: String): String = ""
        override suspend fun getShareableUri(fileUri: String): String = ""
        override suspend fun deleteFile(filePathOrUri: String): Boolean = true
        override suspend fun getFileName(uriString: String): String = ""
        override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String): String? = null
        override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String): String? = null
        override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String): Boolean = true
        override fun openSafeInputStream(uriString: String): InputStream? = null
        override fun openSafeOutputStream(uriString: String): OutputStream? = null
        override suspend fun exportPageAsImage(sourceUri: String, pageIndex: Int, outputUri: String, format: android.graphics.Bitmap.CompressFormat, quality: Int): Boolean = true
        override suspend fun compressPdf(sourceUri: String, destUri: String): com.max97k.pddf.util.Result<Long> = com.max97k.pddf.util.Result.Success(0L)
        override suspend fun addWatermark(sourceUri: String, destUri: String, text: String, opacity: Float, fontSize: Float): Boolean = true
        override suspend fun extractPages(sourceUri: String, destUri: String, pageRange: String): Result<Int> = Result.success(0)
        override suspend fun packageToZip(fileUris: List<String>, outputZipUri: String, onProgress: (Int, Int) -> Unit): Boolean = true
        override suspend fun rotatePages(sourceUri: String, destUri: String, pageIndices: List<Int>?, rotationDegrees: Int): Boolean = true
    }

    @Test
    fun compareIdenticalDocuments_returnsNoDiscrepancies() = runTest(testDispatcher) {
        val meta = PdfMetadata(
            title = "Test",
            author = "Author",
            pageCount = 10,
            fileSizeMb = 1.5,
            pdfVersion = "1.4",
            encryptionMethod = "None",
            canPrint = true,
            canCopy = true,
            version = 1.4f
        )
        val repository = createFakeRepository(mapOf("uri1" to meta, "uri2" to meta))
        val useCase = CompareDocumentsUseCase(repository, testDispatcher)

        val result = useCase.compare("uri1", "uri2")

        assertNotNull(result)
        assertFalse(result!!.pageCountDiffers)
        assertFalse(result.fileSizeDiffers)
        assertFalse(result.encryptionDiffers)
        assertFalse(result.versionDiffers)
        assertFalse(result.permissionsDiffer)
    }

    @Test
    fun compareDisparateDocuments_returnsDiscrepancies() = runTest(testDispatcher) {
        val meta1 = PdfMetadata(
            title = "Test 1",
            author = "Author 1",
            pageCount = 10,
            fileSizeMb = 1.5,
            pdfVersion = "1.4",
            encryptionMethod = "None",
            canPrint = true,
            canCopy = true,
            version = 1.4f
        )
        val meta2 = PdfMetadata(
            title = "Test 2",
            author = "Author 2",
            pageCount = 20,
            fileSizeMb = 2.5,
            pdfVersion = "1.5",
            encryptionMethod = "Standard Encrypted",
            canPrint = false,
            canCopy = false,
            version = 1.5f
        )
        val repository = createFakeRepository(mapOf("uri1" to meta1, "uri2" to meta2))
        val useCase = CompareDocumentsUseCase(repository, testDispatcher)

        val result = useCase.compare("uri1", "uri2")

        assertNotNull(result)
        assertTrue(result!!.pageCountDiffers)
        assertTrue(result.fileSizeDiffers)
        assertTrue(result.encryptionDiffers)
        assertTrue(result.versionDiffers)
        assertTrue(result.permissionsDiffer)
    }
}
