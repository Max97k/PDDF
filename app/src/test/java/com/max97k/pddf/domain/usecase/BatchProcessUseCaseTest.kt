package com.max97k.pddf.domain.usecase

import com.max97k.pddf.ConflictMode
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.domain.repository.PasswordRepository
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
class BatchProcessUseCaseTest {
    private val testDispatcher = StandardTestDispatcher()

    class FakePdfRepository : PdfRepository {
        var createTempFileCalledCount = 0
        var copyCalledCount = 0
        var deleteFileCalledCount = 0

        override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = PdfEncryptionCheck.Encrypted

        override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray): DecryptStatus {
            val password = String(passwordChars)
            return if (password == "correct_password") DecryptStatus.SUCCESS else DecryptStatus.WRONG_PASSWORD
        }

        override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String): DecryptStatus {
            return if (passwordValue == "correct_password") DecryptStatus.SUCCESS else DecryptStatus.WRONG_PASSWORD
        }

        override suspend fun extractMetadata(sourceUri: String, passwordValue: String): PdfMetadata? = null

        override suspend fun copy(sourceUri: String, destUri: String): Boolean {
            copyCalledCount++
            return true
        }

        override suspend fun createTempFile(prefix: String, suffix: String): String {
            createTempFileCalledCount++
            return "temp.pdf"
        }

        override suspend fun getShareableUri(fileUri: String): String = fileUri

        override suspend fun deleteFile(filePathOrUri: String): Boolean {
            deleteFileCalledCount++
            return true
        }

        override suspend fun getFileName(uriString: String): String = "file.pdf"

        override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String): String = "$directoryUriString/$displayName"

        override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String): String? = null

        override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String): Boolean = true
        override fun openSafeInputStream(uriString: String): InputStream? = null
        override fun openSafeOutputStream(uriString: String): OutputStream? = null
    }

    class FakePasswordRepository : PasswordRepository {
        override val allPasswords: Flow<Result<List<PasswordEntity>>> = flowOf(Result.Success(emptyList()))
        override suspend fun getAllDecryptedPasswords(): List<PasswordEntity> = listOf(
            PasswordEntity(name = "Wrong", passwordValue = "wrong_password"),
            PasswordEntity(name = "Correct", passwordValue = "correct_password")
        )
        override suspend fun insert(password: PasswordEntity): Result<Unit> = Result.Success(Unit)
        override suspend fun insert(name: String, passwordChars: CharArray): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteById(id: Int): Result<Unit> = Result.Success(Unit)
        override suspend fun recordPasswordHit(id: Int): Result<Unit> = Result.Success(Unit)
    }

    @Test
    fun testProcessInPlace_usesVaultPasswordsAndWipesAfter() = runTest(testDispatcher) {
        val fakePdfRepo = FakePdfRepository()
        val fakePasswordRepo = FakePasswordRepository()
        val decryptPdfUseCase = DecryptPdfUseCase(fakePdfRepo, testDispatcher)
        val useCase = BatchProcessUseCase(fakePdfRepo, decryptPdfUseCase, fakePasswordRepo, testDispatcher)

        val result = useCase.processInPlace(
            inputUriStrings = listOf("content://pdf/1"),
            passwordValue = "some_other_password", // Even if manual password is wrong, vault will try and succeed
            onProgress = { _, _ -> }
        )

        assertEquals(1, result.successCount)
        assertEquals(0, result.wrongPasswordCount)
        assertEquals(1, fakePdfRepo.createTempFileCalledCount)
        assertEquals(1, fakePdfRepo.copyCalledCount)
        assertEquals(1, fakePdfRepo.deleteFileCalledCount) // temp file deleted
    }

    @Test
    fun testProcessToDirectory_usesVaultPasswordsAndWipesAfter() = runTest(testDispatcher) {
        val fakePdfRepo = FakePdfRepository()
        val fakePasswordRepo = FakePasswordRepository()
        val decryptPdfUseCase = DecryptPdfUseCase(fakePdfRepo, testDispatcher)
        val useCase = BatchProcessUseCase(fakePdfRepo, decryptPdfUseCase, fakePasswordRepo, testDispatcher)

        val result = useCase.processToDirectory(
            inputUriStrings = listOf("content://pdf/1"),
            outputDirectoryUriString = "content://dir",
            passwordValue = "", // Manual password empty, vault will try and succeed
            conflictMode = ConflictMode.SAVE_AS_COPY,
            onProgress = { _, _ -> }
        )

        assertEquals(1, result.successCount)
        assertEquals(0, result.wrongPasswordCount)
        assertEquals(1, fakePdfRepo.createTempFileCalledCount)
        assertEquals(1, fakePdfRepo.copyCalledCount)
        assertEquals(1, fakePdfRepo.deleteFileCalledCount) // temp file deleted
    }
}
