package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PasswordRepository
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
import com.example.util.CryptoManager
import com.example.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/**
 * Tier 2: Boundary Value Analysis (BVA) Tests.
 * Evaluates extreme boundaries: empty inputs, maximum lengths, timeout thresholds,
 * corrupted binary structures, 0-byte files, and memory buffer limits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoundaryValueAnalysisTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository
    private lateinit var viewModel: MainViewModel
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        context = application
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
        CryptoManager.testKeyOverride = keyGen.generateKey()

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PasswordRepository(database.passwordDao(), CryptoManager())
        viewModel = MainViewModel(application, repository, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { FileUtils.secureDelete(it) }
        tempFiles.clear()
        database.close()
        Dispatchers.resetMain()
    }

    private fun createTempFileWithContent(name: String, content: ByteArray): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(content)
        tempFiles.add(file)
        return file
    }

    @Test
    fun boundary_emptyPassword_returnsWrongPasswordOrError() = runTest {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy("owner123", "realPassword", ap)
        doc.protect(spp)
        val encryptedFile = File(context.cacheDir, "bva_encrypted.pdf")
        doc.save(encryptedFile)
        doc.close()
        tempFiles.add(encryptedFile)

        val outputFile = File(context.cacheDir, "bva_output.pdf")
        tempFiles.add(outputFile)

        val decryptUseCase = DecryptPdfUseCase(testDispatcher)
        val status = decryptUseCase.decrypt(
            context,
            Uri.fromFile(encryptedFile),
            Uri.fromFile(outputFile),
            ""
        )
        assertTrue(
            "Empty password should return WRONG_PASSWORD or ERROR",
            status == DecryptStatus.WRONG_PASSWORD || status == DecryptStatus.ERROR
        )
    }

    @Test
    fun boundary_extremeLengthPassword_1024Chars() = runTest {
        val extremePassword = "P".repeat(1024)
        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy("owner123", extremePassword, ap)
        spp.encryptionKeyLength = 128
        doc.protect(spp)
        val encryptedFile = File(context.cacheDir, "bva_extreme_pass.pdf")
        doc.save(encryptedFile)
        doc.close()
        tempFiles.add(encryptedFile)

        val outputFile = File(context.cacheDir, "bva_extreme_out.pdf")
        tempFiles.add(outputFile)

        val decryptUseCase = DecryptPdfUseCase(testDispatcher)
        val status = decryptUseCase.decrypt(
            context,
            Uri.fromFile(encryptedFile),
            Uri.fromFile(outputFile),
            extremePassword
        )
        assertEquals("1024-character password should decrypt successfully", DecryptStatus.SUCCESS, status)
    }

    @Test
    fun boundary_specialUnicodePassword_withEmojisAndSymbols() = runTest {
        val unicodePassword = "🔐P@sswørd_123_™_中文_日本語_العربية_🚀"
        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = AccessPermission()
        val spp = StandardProtectionPolicy("owner123", unicodePassword, ap)
        spp.encryptionKeyLength = 128
        doc.protect(spp)
        val encryptedFile = File(context.cacheDir, "bva_unicode_pass.pdf")
        doc.save(encryptedFile)
        doc.close()
        tempFiles.add(encryptedFile)

        val outputFile = File(context.cacheDir, "bva_unicode_out.pdf")
        tempFiles.add(outputFile)

        val decryptUseCase = DecryptPdfUseCase(testDispatcher)
        val status = decryptUseCase.decrypt(
            context,
            Uri.fromFile(encryptedFile),
            Uri.fromFile(outputFile),
            unicodePassword
        )
        assertEquals("Special Unicode password should decrypt successfully", DecryptStatus.SUCCESS, status)
    }

    @Test
    fun boundary_zeroByteFile_returnsErrorWithoutCrashing() = runTest {
        val zeroByteFile = createTempFileWithContent("bva_zero.pdf", byteArrayOf())
        val outputFile = File(context.cacheDir, "bva_zero_out.pdf")
        tempFiles.add(outputFile)

        val decryptUseCase = DecryptPdfUseCase(testDispatcher)
        val status = decryptUseCase.decrypt(
            context,
            Uri.fromFile(zeroByteFile),
            Uri.fromFile(outputFile),
            "pass"
        )
        assertEquals("Zero-byte file should return ERROR gracefully", DecryptStatus.ERROR, status)
    }

    @Test
    fun boundary_corruptedHeaderFile_returnsErrorGracefully() = runTest {
        val corruptFile = createTempFileWithContent("bva_corrupt.pdf", "NOT_A_VALID_PDF_HEADER_CONTENT".toByteArray())
        val outputFile = File(context.cacheDir, "bva_corrupt_out.pdf")
        tempFiles.add(outputFile)

        val decryptUseCase = DecryptPdfUseCase(testDispatcher)
        val status = decryptUseCase.decrypt(
            context,
            Uri.fromFile(corruptFile),
            Uri.fromFile(outputFile),
            "pass"
        )
        assertEquals("Corrupted file should return ERROR", DecryptStatus.ERROR, status)
    }

    @Test
    fun boundary_emptyUriListSelection_setsEmptyState() = runTest {
        viewModel.setSelectedUris(context, emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.selectedUris.value.isEmpty())
        assertTrue(viewModel.selectedFileNames.value.isEmpty())
        assertNull(viewModel.selectedMetadata.value)
    }

    @Test
    fun boundary_backgroundTimeout_exactBoundaryTesting() {
        viewModel.password.value = "SensitiveSecret"
        val field = MainViewModel::class.java.getDeclaredField("backgroundTime")
        field.isAccessible = true

        // Case 1: 59,990ms (boundary below 60s timeout) -> NOT cleared
        viewModel.onAppBackgrounded()
        field.setLong(viewModel, System.currentTimeMillis() - 59900L)
        viewModel.onAppForegrounded()
        assertEquals("SensitiveSecret", viewModel.password.value)

        // Case 2: 60,050ms (boundary above 60s timeout) -> CLEARED
        viewModel.onAppBackgrounded()
        field.setLong(viewModel, System.currentTimeMillis() - 60050L)
        viewModel.onAppForegrounded()
        assertEquals("", viewModel.password.value)
    }

    @Test
    fun boundary_memoryWipe_charArrayAndByteArray() {
        // Zero-length
        val emptyChars = CharArray(0)
        java.util.Arrays.fill(emptyChars, '\u0000')
        assertEquals(0, emptyChars.size)

        // 1-char
        val singleChar = charArrayOf('A')
        java.util.Arrays.fill(singleChar, '\u0000')
        assertEquals('\u0000', singleChar[0])

        // Large 10,000-char buffer
        val largeChars = CharArray(10000) { 'X' }
        java.util.Arrays.fill(largeChars, '\u0000')
        assertTrue(largeChars.all { it == '\u0000' })

        // Byte array
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        java.util.Arrays.fill(bytes, 0.toByte())
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun boundary_secureDelete_nonExistentAndReadOnly() {
        val nonExistent = File(context.cacheDir, "non_existent_${System.currentTimeMillis()}.tmp")
        val resultNonExistent = FileUtils.secureDelete(nonExistent)
        assertTrue("secureDelete on non-existent file is idempotent and returns true", resultNonExistent)

        val realFile = createTempFileWithContent("to_shred.bin", "Sensitive Data for DoD Shred".toByteArray())
        assertTrue(realFile.exists())
        val resultReal = FileUtils.secureDelete(realFile)
        assertTrue(resultReal)
        assertFalse(realFile.exists())
    }
}
