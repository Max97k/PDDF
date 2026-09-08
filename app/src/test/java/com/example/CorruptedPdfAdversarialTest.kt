package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.data.AppDatabase
import com.example.data.PasswordRepository
import com.example.domain.usecase.AutoUnlockUseCase
import com.example.domain.usecase.BatchProcessUseCase
import com.example.domain.usecase.DecryptPdfUseCase
import com.example.domain.usecase.PasswordVaultUseCase
import com.example.util.CryptoManager
import com.example.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
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
import java.io.IOException
import java.util.Random
import javax.crypto.KeyGenerator

/**
 * M4 Adversarial Hardening Test:
 * Thoroughly exercises corrupted PDF header defense, unsupported encryption DRM handling,
 * multi-file selection warnings, and UI error boundary notifications (Snackbar / Toast),
 * ensuring the application never hangs or enters invalid retry loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorruptedPdfAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository
    private lateinit var decryptUseCase: DecryptPdfUseCase
    private lateinit var vaultUseCase: PasswordVaultUseCase
    private lateinit var autoUnlockUseCase: AutoUnlockUseCase
    private lateinit var batchProcessUseCase: BatchProcessUseCase
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
        vaultUseCase = PasswordVaultUseCase(repository)
        decryptUseCase = DecryptPdfUseCase(testDispatcher)
        autoUnlockUseCase = AutoUnlockUseCase(decryptUseCase, vaultUseCase, testDispatcher)
        batchProcessUseCase = BatchProcessUseCase(decryptUseCase, testDispatcher)

        viewModel = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = decryptUseCase,
            passwordVaultUseCase = vaultUseCase,
            autoUnlockUseCase = autoUnlockUseCase,
            batchProcessUseCase = batchProcessUseCase
        )
    }

    @After
    fun tearDown() {
        tempFiles.forEach { FileUtils.secureDelete(it) }
        tempFiles.clear()
        database.close()
        Dispatchers.resetMain()
    }

    private fun createCorruptedFile(name: String, bytes: ByteArray): File {
        val file = File(context.cacheDir, "$name.pdf")
        file.writeBytes(bytes)
        tempFiles.add(file)
        return file
    }

    // ==========================================
    // 1. CORRUPTED HEADER DEFENSE MATRIX
    // ==========================================

    @Test
    fun autoUnlock_adversarialCorruptedHeaders_returnExplicitError() = runTest(testDispatcher) {
        val corruptedPayloads = listOf(
            "plain_text" to "This is a plain text file pretending to be a PDF".toByteArray(),
            "fake_zip" to byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00),
            "html_page" to "<!DOCTYPE html><html><body><h1>500 Server Error</h1></body></html>".toByteArray(),
            "truncated_magic" to "%PDF-".toByteArray(),
            "binary_noise" to ByteArray(2048).also { Random(42).nextBytes(it) },
            "zero_bytes" to ByteArray(0)
        )

        for ((name, payload) in corruptedPayloads) {
            val file = createCorruptedFile("corrupt_$name", payload)
            val uri = Uri.fromFile(file)

            val result = autoUnlockUseCase.tryAutoUnlock(context, uri)
            assertTrue("Payload '$name' must return AutoUnlockResult.Error", result is AutoUnlockUseCase.AutoUnlockResult.Error)

            val error = result as AutoUnlockUseCase.AutoUnlockResult.Error
            assertTrue(
                "Error message for '$name' must describe corrupted header or file",
                error.message.contains("Corrupted PDF header or file")
            )
        }
    }

    @Test
    fun handleExternalPdfIntent_corruptedHeader_showsSnackbarAndSuppressesPasswordPrompt() = runTest(testDispatcher) {
        val corruptFile = createCorruptedFile("intent_corrupted", "NOT_A_VALID_PDF".toByteArray())
        val uri = Uri.fromFile(corruptFile)

        viewModel.uiEffect.test {
            viewModel.handleExternalPdfIntent(context, uri)
            advanceUntilIdle()

            // Verify password prompt is NEVER shown on corrupted header
            assertFalse("Password prompt must be false for corrupted PDF", viewModel.showAutoUnlockPasswordPrompt.value)
            assertFalse("Auto-unlocking state must be false", viewModel.isAutoUnlocking.value)
            assertTrue("Status message must reflect corrupted error", viewModel.statusMessage.value.orEmpty().contains("Corrupted PDF header or file"))

            // Verify Snackbar effect was emitted for visible user feedback
            val effect = awaitItem()
            assertTrue("Expected ShowSnackbar effect", effect is UiEffect.ShowSnackbar)
            val snackbar = effect as UiEffect.ShowSnackbar
            assertTrue(snackbar.message.contains("Corrupted PDF header or file"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==========================================
    // 2. RETRY LOOP PREVENTION ON UNSUPPORTED DRM
    // ==========================================

    @Test
    fun unlockWithManualPassword_unsupportedEncryption_dismissesDialogAndEmitsSnackbar() = runTest(testDispatcher) {
        val unsupportedUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override suspend fun decrypt(context: Context, inputUri: Uri, outputUri: Uri, passwordValue: String): DecryptStatus {
                return DecryptStatus.UNSUPPORTED_ENCRYPTION
            }
        }
        val customVm = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = unsupportedUseCase
        )

        val dummyUri = Uri.parse("content://saf/drm_encrypted.pdf")
        customVm.showAutoUnlockPasswordPrompt.value = true

        customVm.uiEffect.test {
            customVm.unlockWithManualPassword(
                context = context,
                uri = dummyUri,
                enteredPassword = "some_guess",
                rememberPassword = false
            )
            advanceUntilIdle()

            // Verify dialog is immediately dismissed to break retry loops
            assertFalse("Dialog must be dismissed on unsupported encryption", customVm.showAutoUnlockPasswordPrompt.value)

            val expectedWarning = context.getString(R.string.summary_unsupported, 1)
            assertEquals(expectedWarning, customVm.statusMessage.value)

            val effect = awaitItem()
            assertTrue("Snackbar must notify user of unsupported encryption", effect is UiEffect.ShowSnackbar)
            val snackbar = effect as UiEffect.ShowSnackbar
            assertEquals(expectedWarning, snackbar.message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==========================================
    // 3. MULTI-FILE CORRUPTED DETECTION & FEEDBACK
    // ==========================================

    @Test
    fun checkSelectedPdfs_mixOfValidAndCorrupted_surfacesCorruptedWarnings() = runTest(testDispatcher) {
        // 1. Valid PDF
        val validFile = File(context.cacheDir, "valid_item.pdf")
        val doc = PDDocument()
        doc.addPage(PDPage())
        doc.save(validFile)
        doc.close()
        tempFiles.add(validFile)

        // 2. Corrupted PDF
        val corruptFile1 = createCorruptedFile("corrupt_item_alpha", "INVALID_HEADER_1".toByteArray())
        val corruptFile2 = createCorruptedFile("corrupt_item_beta", ByteArray(0))

        val uris = listOf(
            Uri.fromFile(validFile),
            Uri.fromFile(corruptFile1),
            Uri.fromFile(corruptFile2)
        )

        viewModel.uiEffect.test {
            viewModel.setSelectedUris(context, uris)
            advanceUntilIdle()

            // Verify status message warns about corrupted files
            val status = viewModel.statusMessage.value.orEmpty()
            assertTrue("Status must mention corrupted files", status.contains("is corrupted or unreadable"))
            assertTrue("Status must list corrupt_item_alpha", status.contains("corrupt_item_alpha.pdf"))
            assertTrue("Status must list corrupt_item_beta", status.contains("corrupt_item_beta.pdf"))

            // Verify Snackbar emission
            val effect = awaitItem()
            assertTrue("Snackbar must be emitted for multi-file corrupted warnings", effect is UiEffect.ShowSnackbar)
            val snackbar = effect as UiEffect.ShowSnackbar
            assertTrue(snackbar.message.contains("corrupt_item_alpha.pdf"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun autoUnlock_unsupportedSecurityHandlerKeywords_returnsUnsupportedResult() = runTest(testDispatcher) {
        val drmUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeInputStream(context: Context, uri: Uri): java.io.InputStream {
                return object : java.io.InputStream() {
                    override fun read(): Int = throw IOException("Unknown SecurityHandler CryptFilter Adobe DRM unsupported")
                }
            }
        }
        val customAutoUnlock = AutoUnlockUseCase(drmUseCase, vaultUseCase, testDispatcher)

        val dummyUri = Uri.parse("content://saf/adobe_drm.pdf")
        val result = customAutoUnlock.tryAutoUnlock(context, dummyUri)

        assertTrue(result is AutoUnlockUseCase.AutoUnlockResult.Error)
        val error = result as AutoUnlockUseCase.AutoUnlockResult.Error
        assertEquals("Unsupported encryption/DRM", error.message)
    }
}
