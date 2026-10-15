package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class BatchProcessUseCaseTest {

    class FakeRepository : PdfRepository {
        override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = PdfEncryptionCheck.NotEncrypted
        override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray): DecryptStatus = DecryptStatus.ERROR
        override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String): DecryptStatus = DecryptStatus.ERROR
        override suspend fun extractMetadata(sourceUri: String, passwordValue: String): PdfMetadata? = null
        override suspend fun copy(sourceUri: String, destUri: String): Boolean = true
        override suspend fun createTempFile(prefix: String, suffix: String): String = "temp_uri"
        override suspend fun getShareableUri(fileUri: String): String = fileUri
        override suspend fun deleteFile(filePathOrUri: String): Boolean = true
        override suspend fun getFileName(uriString: String): String = "test_file.pdf"
        override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String): String? = null
        override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String): String? = null
        override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String): Boolean = true
        override fun openSafeInputStream(uriString: String): InputStream? = null
        override fun openSafeOutputStream(uriString: String): OutputStream? = null
    }

    class FakeDecryptPdfUseCase : DecryptPdfUseCase(FakeRepository(), kotlinx.coroutines.Dispatchers.Unconfined) {
        override suspend fun decrypt(inputUriString: String, outputUriString: String, passwordValue: String): DecryptStatus {
            return if (inputUriString.contains("fail")) DecryptStatus.WRONG_PASSWORD else DecryptStatus.SUCCESS
        }
    }

    @Test
    fun `processInPlace correctly populates failedFiles on error`() = runTest {
        val fakeRepository = FakeRepository()
        val fakeDecryptUseCase = FakeDecryptPdfUseCase()
        val useCase = BatchProcessUseCase(fakeRepository, fakeDecryptUseCase, kotlinx.coroutines.Dispatchers.Unconfined)

        val result = useCase.processInPlace(
            inputUriStrings = listOf("content://test_fail_uri"),
            passwordValue = "wrong",
            onProgress = { _, _ -> }
        )

        assertEquals(1, result.wrongPasswordCount)
        assertEquals(1, result.failedFiles.size)
        assertEquals("test_file.pdf", result.failedFiles.first().fileName)
        assertEquals(DecryptStatus.WRONG_PASSWORD, result.failedFiles.first().errorType)
    }

    @Test
    fun `processInPlace partial retry logic simulated`() = runTest {
        val fakeRepository = FakeRepository()
        val fakeDecryptUseCase = FakeDecryptPdfUseCase()
        val useCase = BatchProcessUseCase(fakeRepository, fakeDecryptUseCase, kotlinx.coroutines.Dispatchers.Unconfined)

        // Simulating the user passing only the failed ones to the use case (MainViewModel handles combination)
        val result = useCase.processInPlace(
            inputUriStrings = listOf("content://test_success_uri"),
            passwordValue = "correct",
            onProgress = { _, _ -> }
        )

        assertEquals(1, result.successCount)
        assertEquals(0, result.failedFiles.size)
    }
}
