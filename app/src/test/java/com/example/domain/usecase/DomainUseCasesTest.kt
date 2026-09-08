package com.example.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.DecryptStatus
import com.example.data.PasswordDao
import com.example.data.PasswordEntity
import com.example.data.PasswordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainUseCasesTest {

    private lateinit var context: Context
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
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPasswordVaultUseCase_insertAndDelete() = runTest(testDispatcher) {
        val fakeDao = FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)

        vaultUseCase.insertPassword("Test Bank", "secret123")
        val saved = vaultUseCase.getAllDecryptedPasswords()
        assertEquals(1, saved.size)
        assertEquals("Test Bank", saved[0].name)
        assertEquals("secret123", saved[0].passwordValue)
    }

    @Test
    fun testDecryptPdfUseCase_nonExistentFile_returnsError() = runTest(testDispatcher) {
        val decryptPdfUseCase = DecryptPdfUseCase(testDispatcher)
        val dummyInput = Uri.fromFile(File(context.cacheDir, "non_existent_input.pdf"))
        val dummyOutput = Uri.fromFile(File(context.cacheDir, "non_existent_output.pdf"))

        val status = decryptPdfUseCase.decrypt(context, dummyInput, dummyOutput, "somepass")
        assertEquals(DecryptStatus.ERROR, status)
    }

    @Test
    fun testAutoUnlockUseCase_corruptedHeader_returnsError() = runTest(testDispatcher) {
        val decryptPdfUseCase = DecryptPdfUseCase(testDispatcher)
        val fakeDao = FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(decryptPdfUseCase, vaultUseCase, testDispatcher)

        val corruptFile = File.createTempFile("corrupt_test", ".pdf", context.cacheDir)
        corruptFile.writeBytes("NOT_A_VALID_PDF_HEADER_AT_ALL".toByteArray())
        corruptFile.deleteOnExit()

        val result = autoUnlockUseCase.tryAutoUnlock(context, Uri.fromFile(corruptFile))
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertTrue(errorResult.message.contains("Corrupted PDF header or file"))
    }

    @Test
    fun testAutoUnlockUseCase_zeroByteFile_returnsError() = runTest(testDispatcher) {
        val decryptPdfUseCase = DecryptPdfUseCase(testDispatcher)
        val fakeDao = FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(decryptPdfUseCase, vaultUseCase, testDispatcher)

        val zeroByteFile = File.createTempFile("zero_byte_test", ".pdf", context.cacheDir)
        zeroByteFile.writeBytes(ByteArray(0))
        zeroByteFile.deleteOnExit()

        val result = autoUnlockUseCase.tryAutoUnlock(context, Uri.fromFile(zeroByteFile))
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertTrue(errorResult.message.contains("Corrupted PDF header or file"))
    }

    @Test
    fun testAutoUnlockUseCase_nullStream_returnsError() = runTest(testDispatcher) {
        val nullStreamUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeInputStream(context: Context, uri: Uri): java.io.InputStream? = null
        }
        val fakeDao = FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)
        val autoUnlockUseCase = AutoUnlockUseCase(nullStreamUseCase, vaultUseCase, testDispatcher)

        val nonExistentUri = Uri.parse("content://non.existent.provider/dummy.pdf")
        val result = autoUnlockUseCase.tryAutoUnlock(context, nonExistentUri)
        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val errorResult = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertEquals("Unable to open file stream", errorResult.message)
    }

    @Test
    fun testDecryptPdfUseCase_safStreamInterruption_returnsError() = runTest(testDispatcher) {
        val interruptingUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeInputStream(context: Context, uri: Uri): java.io.InputStream {
                return object : java.io.InputStream() {
                    override fun read(): Int = throw java.io.IOException("SAF stream aborted abruptly")
                }
            }
        }
        val dummyInput = Uri.parse("content://saf/interrupted.pdf")
        val dummyOutputFile = File.createTempFile("out", ".pdf", context.cacheDir)
        dummyOutputFile.deleteOnExit()
        val dummyOutput = Uri.fromFile(dummyOutputFile)

        val status = interruptingUseCase.decrypt(context, dummyInput, dummyOutput, "password")
        assertEquals(DecryptStatus.ERROR, status)
    }

    @Test
    fun testDecryptPdfUseCase_nullOutputStream_returnsError() = runTest(testDispatcher) {
        val nullOutputUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeOutputStream(context: Context, uri: Uri): java.io.OutputStream? {
                return null
            }
        }

        // Create a valid unencrypted PDF
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        val validFile = File.createTempFile("valid_doc", ".pdf", context.cacheDir)
        doc.save(validFile)
        doc.close()
        validFile.deleteOnExit()

        val outputUri = Uri.parse("content://saf/null_destination.pdf")
        val status = nullOutputUseCase.decrypt(context, Uri.fromFile(validFile), outputUri, "")
        assertEquals(DecryptStatus.ERROR, status)
    }
}
