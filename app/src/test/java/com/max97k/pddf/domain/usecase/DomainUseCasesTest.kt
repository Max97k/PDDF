package com.max97k.pddf.domain.usecase

import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.FakeAppUpdateRepository
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.domain.model.AppUpdateStatus
import com.max97k.pddf.domain.repository.PasswordRepository
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
class DomainUseCasesTest {
    private val testDispatcher = StandardTestDispatcher()

    class FakePdfRepository(
        private val isEncryptedResult: PdfEncryptionCheck = PdfEncryptionCheck.Encrypted,
        private val decryptResult: DecryptStatus = DecryptStatus.SUCCESS,
        private val extractMetadataResult: PdfMetadata? = PdfMetadata(
            title = "",
            author = "",
            pageCount = 0,
            fileSizeMb = 0.0,
            encryptionMethod = "",
            canPrint = true,
            canCopy = true
        ),
        private val copyResult: Boolean = true,
        private val createTempFileResult: String = "temp.pdf",
        private val deleteFileResult: Boolean = true
    ) : PdfRepository {
        var copyCalledCount = 0

        override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = isEncryptedResult

        override suspend fun decrypt(
            sourceUri: String,
            destUri: String,
            passwordChars: CharArray
        ): DecryptStatus = decryptResult

        override suspend fun decrypt(
            sourceUri: String,
            destUri: String,
            passwordValue: String
        ): DecryptStatus = decryptResult

        override suspend fun extractMetadata(
            sourceUri: String,
            passwordValue: String
        ): PdfMetadata? = extractMetadataResult

        override suspend fun copy(sourceUri: String, destUri: String): Boolean {
            copyCalledCount++
            return copyResult
        }

        override suspend fun createTempFile(prefix: String, suffix: String): String = createTempFileResult
        override suspend fun getShareableUri(fileUri: String): String = fileUri
        override suspend fun deleteFile(filePathOrUri: String): Boolean = deleteFileResult
        override suspend fun getFileName(uriString: String): String = "file.pdf"
        override suspend fun createDocumentInDirectory(
            directoryUriString: String,
            displayName: String,
            mimeType: String
        ): String = "$directoryUriString/$displayName"

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

    class FakePasswordDao {
        private val passwords = mutableListOf<PasswordEntity>()
        private var nextId = 1

        val allPasswordsFlow = MutableStateFlow(passwords.toList())

        suspend fun getAllPasswords(): List<PasswordEntity> {
            return passwords.toList()
        }

        suspend fun insertPassword(password: PasswordEntity) {
            val copy = password.copy(id = nextId++)
            passwords.add(copy)
            allPasswordsFlow.value = passwords.toList()
        }

        suspend fun deletePasswordById(id: Int) {
            passwords.removeIf { it.id == id }
            allPasswordsFlow.value = passwords.toList()
        }

        suspend fun getPasswordById(id: Int): PasswordEntity? {
            return passwords.find { it.id == id }
        }

        suspend fun incrementUseCount(id: Int, timestamp: Long) {
            val index = passwords.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = passwords[index]
                passwords[index] = current.copy(
                    useCount = current.useCount + 1,
                    lastUsedAt = timestamp
                )
                allPasswordsFlow.value = passwords.toList()
            }
        }
    }

    class FakePasswordRepository(private val fakeDao: FakePasswordDao) : PasswordRepository {
        override val allPasswords: Flow<Result<List<PasswordEntity>>> = fakeDao.allPasswordsFlow.map { Result.Success(it) }

        override suspend fun getAllDecryptedPasswords(): List<PasswordEntity> {
            return fakeDao.getAllPasswords()
        }

        override suspend fun insert(password: PasswordEntity): Result<Unit> {
            fakeDao.insertPassword(password)
            return Result.Success(Unit)
        }

        override suspend fun insert(name: String, passwordChars: CharArray): Result<Unit> {
            fakeDao.insertPassword(PasswordEntity(name = name, passwordChars = passwordChars))
            return Result.Success(Unit)
        }

        override suspend fun deleteById(id: Int): Result<Unit> {
            fakeDao.deletePasswordById(id)
            return Result.Success(Unit)
        }

        override suspend fun recordPasswordHit(id: Int): Result<Unit> {
            fakeDao.incrementUseCount(id, System.currentTimeMillis())
            return Result.Success(Unit)
        }
    }

    @Test
    fun testDecryptPdfUseCase_decryptWithChars() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.SUCCESS)
        val useCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val status = useCase.decrypt("content://source", "content://dest", charArrayOf('a'))
        assertEquals(DecryptStatus.SUCCESS, status)
    }

    @Test
    fun testDecryptPdfUseCase_decryptWithString() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.SUCCESS)
        val useCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val status = useCase.decrypt("content://source", "content://dest", "pass")
        assertEquals(DecryptStatus.SUCCESS, status)
    }

    @Test
    fun testDecryptPdfUseCase_extractMetadata() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository()
        val useCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val metadata = useCase.extractMetadata("content://source", "pass")
        assertEquals("", metadata?.title)
    }

    @Test
    fun testPasswordVaultUseCase_insertAndDelete() = runTest(testDispatcher) {
        val dao = FakePasswordDao()
        val repo = FakePasswordRepository(dao)
        val vaultUseCase = PasswordVaultUseCase(repo)

        vaultUseCase.insertPassword("Test1", "pass1")
        val passwords = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(1, passwords.size)
        assertEquals("Test1", passwords[0].name)

        vaultUseCase.deletePassword(passwords[0].id)
        assertEquals(0, vaultUseCase.getAllDecryptedPasswords().size)
    }

    @Test
    fun testPasswordVaultUseCase_insertEmptyPassword_ignored() = runTest(testDispatcher) {
        val dao = FakePasswordDao()
        val repo = FakePasswordRepository(dao)
        val vaultUseCase = PasswordVaultUseCase(repo)

        vaultUseCase.insertPassword("Test1", "")
        assertEquals(0, vaultUseCase.getAllDecryptedPasswords().size)
    }

    @Test
    fun testPasswordVaultUseCase_insertEmptyName_defaultsToGeneric() = runTest(testDispatcher) {
        val dao = FakePasswordDao()
        val repo = FakePasswordRepository(dao)
        val vaultUseCase = PasswordVaultUseCase(repo)

        vaultUseCase.insertPassword("", "pass")
        val passwords = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(1, passwords.size)
        assertEquals("PDF Password", passwords[0].name)
    }

    @Test
    fun testAutoUnlockUseCase_unlock_success() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(isEncryptedResult = PdfEncryptionCheck.Encrypted, decryptResult = DecryptStatus.SUCCESS)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val vaultUseCase = PasswordVaultUseCase(FakePasswordRepository(FakePasswordDao()))
        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("content://pdf")
        assertEquals(AutoUnlockUseCase.AutoUnlockResult.RequireManualPassword, result)
    }

    @Test
    fun testAutoUnlockUseCase_successfulAutoUnlockWithSavedPassword() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(isEncryptedResult = PdfEncryptionCheck.Encrypted, decryptResult = DecryptStatus.SUCCESS)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)

        val dao = FakePasswordDao()
        val repo = FakePasswordRepository(dao)
        val vaultUseCase = PasswordVaultUseCase(repo)
        vaultUseCase.insertPassword("CorrectVaultPass", "real_pass")

        val autoUnlockUseCase = AutoUnlockUseCase(fakeRepo, decryptPdfUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock("content://pdf")

        assert(result is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword)
        if (result is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword) {
            assertEquals("temp.pdf", result.outputUri)
            assertEquals("CorrectVaultPass", result.matchedPasswordName)
        }
    }

    @Test
    fun testBatchProcessUseCase_processInPlace_success() = runTest(testDispatcher) {
        val fakeRepo = FakePdfRepository(decryptResult = DecryptStatus.SUCCESS)
        val decryptPdfUseCase = DecryptPdfUseCase(fakeRepo, testDispatcher)
        val passwordRepository = FakePasswordRepository(FakePasswordDao())
        val batchProcessUseCase = BatchProcessUseCase(fakeRepo, decryptPdfUseCase, passwordRepository, testDispatcher)

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
        val fakeAppUpdateRepo = FakeAppUpdateRepository()
        val useCase = CheckAppUpdateUseCase(fakeAppUpdateRepo)

        assertEquals(AppUpdateStatus.Idle, useCase.updateStatus.value)

        useCase.checkForUpdate()
        assertEquals(AppUpdateStatus.UpToDate, useCase.updateStatus.value)

        useCase.completeUpdate()
        assertEquals(AppUpdateStatus.Idle, useCase.updateStatus.value)
    }
}
