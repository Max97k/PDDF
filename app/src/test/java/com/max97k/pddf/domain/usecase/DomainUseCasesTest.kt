package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.data.PasswordDao
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.domain.repository.PasswordRepository
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class DomainUseCasesTest {

    private val testDispatcher = StandardTestDispatcher()

    class FakePasswordDao : PasswordDao {
        private val list = mutableListOf<PasswordEntity>()

        override fun getAllPasswords(): Flow<List<PasswordEntity>> = flowOf(list.toList())

        override suspend fun getAllPasswordsList(): List<PasswordEntity> = list.toList()

        override suspend fun insertPassword(password: PasswordEntity) {
            list.removeAll { it.id == password.id && password.id != 0 }
            list.add(password)
        }

        override suspend fun deletePasswordById(id: Int) {
            list.removeAll { it.id == id }
        }

        override suspend fun incrementUsage(id: Int, timestamp: Long) {
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = list[index]
                list[index] = current.copy(
                    useCount = current.useCount + 1,
                    lastUsedAt = timestamp
                )
            }
        }

        override suspend fun clearAllPasswords() {
            list.clear()
        }

        override suspend fun deletePassword(password: PasswordEntity) {
            list.removeAll { it.id == password.id }
        }
    }

    class FakePasswordRepository : PasswordRepository {
        private val list = mutableListOf<PasswordEntity>()
        private var nextId = 1

        override val allPasswords: Flow<com.max97k.pddf.util.Result<List<PasswordEntity>>> =
            flowOf(com.max97k.pddf.util.Result.Success(list.toList()))

        override suspend fun getAllDecryptedPasswords(): List<PasswordEntity> = list.toList()

        override suspend fun insert(password: PasswordEntity): com.max97k.pddf.util.Result<Unit> {
            val assignedId = if (password.id == 0) nextId++ else password.id
            list.removeAll { it.id == assignedId }
            list.add(password.copy(id = assignedId))
            return com.max97k.pddf.util.Result.Success(Unit)
        }

        override suspend fun insert(name: String, passwordChars: CharArray): com.max97k.pddf.util.Result<Unit> {
            val entity = PasswordEntity(
                id = nextId++,
                name = name.ifBlank { "PDF Password" },
                passwordValue = String(passwordChars)
            )
            list.add(entity)
            return com.max97k.pddf.util.Result.Success(Unit)
        }

        override suspend fun deleteById(id: Int): com.max97k.pddf.util.Result<Unit> {
            list.removeAll { it.id == id }
            return com.max97k.pddf.util.Result.Success(Unit)
        }

        override suspend fun recordPasswordHit(id: Int): com.max97k.pddf.util.Result<Unit> {
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = list[index]
                list[index] = current.copy(
                    useCount = current.useCount + 1,
                    lastUsedAt = System.currentTimeMillis()
                )
            }
            return com.max97k.pddf.util.Result.Success(Unit)
        }
    }

    class FakePdfRepository(
        var checkEncryptionResult: PdfEncryptionCheck = PdfEncryptionCheck.NotEncrypted,
        var decryptResult: DecryptStatus = DecryptStatus.SUCCESS,
        var metadataResult: PdfMetadata? = null,
        var copyResult: Boolean = true
    ) : PdfRepository {
        var lastDecryptedInput: String? = null
        var lastDecryptedOutput: String? = null
        var lastDecryptedPasswordValue: String? = null

        override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = checkEncryptionResult

        override suspend fun extractPages(sourceUri: String, destUri: String, pageRange: String): Result<Int> {
            return Result.success(1)
        }

        override suspend fun decrypt(
            sourceUri: String,
            destUri: String,
            passwordChars: CharArray
        ): DecryptStatus {
            lastDecryptedInput = sourceUri
            lastDecryptedOutput = destUri
            lastDecryptedPasswordValue = String(passwordChars)
            return decryptResult
        }

        override suspend fun decrypt(
            sourceUri: String,
            destUri: String,
            passwordValue: String
        ): DecryptStatus {
            lastDecryptedInput = sourceUri
            lastDecryptedOutput = destUri
            lastDecryptedPasswordValue = passwordValue
            return decryptResult
        }

        override suspend fun extractMetadata(
            sourceUri: String,
            passwordValue: String
        ): PdfMetadata? = metadataResult

        override suspend fun copy(
            sourceUri: String,
            destUri: String
        ): Boolean = copyResult

        override suspend fun createTempFile(
            prefix: String,
            suffix: String
        ): String = "temp://$prefix$suffix"

        override suspend fun getShareableUri(fileUri: String): String =
            "content://com.max97k.pddf.fileprovider/$fileUri"

        override suspend fun deleteFile(filePathOrUri: String): Boolean = true

        override suspend fun getFileName(uriString: String): String =
            uriString.substringAfterLast("/")

        override suspend fun createDocumentInDirectory(
            directoryUriString: String,
            displayName: String,
            mimeType: String
        ): String? = "$directoryUriString/$displayName"

        override suspend fun findDocumentInDirectory(
            directoryUriString: String,
            displayName: String
        ): String? = null

        override suspend fun deleteDocumentInDirectory(
            directoryUriString: String,
            displayName: String
        ): Boolean = true

        override fun openSafeInputStream(uriString: String): InputStream? = null

        override fun openSafeOutputStream(uriString: String): OutputStream? = null
    }

    @Test
    fun testPasswordVaultUseCase_insertAndDelete() = runTest(testDispatcher) {
        val repository = FakePasswordRepository()
        val vaultUseCase = PasswordVaultUseCase(repository)

        vaultUseCase.insertPassword("Test Bank", "secret123")
        val saved = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(1, saved.size)
        assertEquals("Test Bank", saved[0].name)
        assertEquals("secret123", saved[0].passwordValue)

        vaultUseCase.deletePassword(saved[0].id)
        val afterDelete = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun testDecryptPdfUseCase_nonExistentFile_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.ERROR)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val status = decryptPdfUseCase.decrypt("file:///non_existent_input.pdf", "file:///non_existent_output.pdf", "somepass")
        assertEquals(DecryptStatus.ERROR, status)
    }

    @Test
    fun testAutoUnlockUseCase_corruptedHeader_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(checkEncryptionResult = PdfEncryptionCheck.Error("Corrupted PDF header or file"))
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val repository = FakePasswordRepository()
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("file:///corrupt_test.pdf")
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertTrue(errorResult.message.contains("Corrupted PDF header or file"))
    }

    @Test
    fun testAutoUnlockUseCase_zeroByteFile_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(checkEncryptionResult = PdfEncryptionCheck.Error("Corrupted PDF header or file"))
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val repository = FakePasswordRepository()
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("file:///zero_byte.pdf")
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertTrue(errorResult.message.contains("Corrupted PDF header or file"))
    }

    @Test
    fun testAutoUnlockUseCase_nullStream_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(checkEncryptionResult = PdfEncryptionCheck.Error("Unable to open file stream"))
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val repository = FakePasswordRepository()
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("content://non.existent.provider/dummy.pdf")
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertEquals("Unable to open file stream", errorResult.message)
    }

    @Test
    fun testDecryptPdfUseCase_safStreamInterruption_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.ERROR)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val status = decryptPdfUseCase.decrypt("content://saf/interrupted.pdf", "file:///out.pdf", "password")
        assertEquals(DecryptStatus.ERROR, status)
    }

    @Test
    fun testDecryptPdfUseCase_nullOutputStream_returnsError() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.ERROR)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val status = decryptPdfUseCase.decrypt("file:///valid_doc.pdf", "content://saf/null_destination.pdf", "")
        assertEquals(DecryptStatus.ERROR, status)
    }

    @Test
    fun testAutoUnlockUseCase_successfulAutoUnlockWithSavedPassword() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(
            checkEncryptionResult = PdfEncryptionCheck.Encrypted,
            decryptResult = DecryptStatus.SUCCESS
        )
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val repository = FakePasswordRepository()
        val vaultUseCase = PasswordVaultUseCase(repository)
        vaultUseCase.insertPassword("Citibank", "secret")

        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("content://saf/statement.pdf")
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword)
        val unlocked = result as AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword
        assertEquals("Citibank", unlocked.matchedPasswordName)
        assertTrue(unlocked.outputUri.contains("content://com.max97k.pddf.fileprovider/"))
    }

    @Test
    fun testBatchProcessUseCase_processInPlace_success() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.SUCCESS)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val batchProcessUseCase = BatchProcessUseCase(fakeRepo, decryptPdfUseCase, testDispatcher)

        val result = batchProcessUseCase.processInPlace(
            inputUriStrings = listOf("content://pdf/1", "content://pdf/2"),
            passwordValue = "1234",
            onProgress = { _, _ -> }
        )

        assertEquals(2, result.successCount)
        assertEquals(0, result.errorCount)
        assertEquals("content://pdf/2", result.lastDecryptedUri)
    }

    @Test
    fun testCheckAppUpdateUseCase_operations() = runTest(testDispatcher) {
        val fakeAppUpdateRepo = com.max97k.pddf.FakeAppUpdateRepository()
        val useCase = CheckAppUpdateUseCase(fakeAppUpdateRepo)

        assertEquals(com.max97k.pddf.domain.model.AppUpdateStatus.Idle, useCase.updateStatus.value)

        useCase.checkForUpdate()
        assertTrue(fakeAppUpdateRepo.checkForUpdateCalled)
        assertEquals(com.max97k.pddf.domain.model.AppUpdateStatus.UpToDate, useCase.updateStatus.value)

        val launched = useCase.startFlexibleUpdate("dummy_launcher")
        assertTrue(fakeAppUpdateRepo.startFlexibleUpdateCalled)
        assertTrue(launched)

        useCase.completeUpdate()
        assertTrue(fakeAppUpdateRepo.completeUpdateCalled)

        fakeAppUpdateRepo.setStatus(com.max97k.pddf.domain.model.AppUpdateStatus.Failed())
        assertEquals(com.max97k.pddf.domain.model.AppUpdateStatus.Failed(), useCase.updateStatus.value)

        useCase.resetStatus()
        assertEquals(com.max97k.pddf.domain.model.AppUpdateStatus.Idle, useCase.updateStatus.value)
    }
}
