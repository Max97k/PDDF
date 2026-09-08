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
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.KeyGenerator

/**
 * M4 Adversarial Hardening Test:
 * Verifies that saving from the PDF viewer never produces 0-byte or corrupted files,
 * guarantees error feedback on empty or failed streams, and auto-closes streams without hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfViewerSaveAdversarialTest {

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

    private fun createValidPdf(name: String): File {
        val file = File(context.cacheDir, "$name.pdf")
        val doc = PDDocument()
        doc.addPage(PDPage())
        doc.documentInformation.title = "Save Verification Document"
        doc.save(file)
        doc.close()
        tempFiles.add(file)
        return file
    }

    @Test
    fun saveFromViewer_validPdf_producesNonZeroByteValidFile() = runTest(testDispatcher) {
        val validPdf = createValidPdf("viewer_preview_valid")
        val sourceUri = Uri.fromFile(validPdf)

        val outputFile = File(context.cacheDir, "viewer_saved_output.pdf")
        if (outputFile.exists()) outputFile.delete()
        tempFiles.add(outputFile)
        val destUri = Uri.fromFile(outputFile)

        viewModel.uiEffect.test {
            viewModel.copyUriStream(context, sourceUri, destUri)
            advanceUntilIdle()

            // Verify file length is identical and non-zero
            assertTrue("Output file must exist", outputFile.exists())
            assertTrue("Output file must not be 0 bytes", outputFile.length() > 0L)
            assertEquals("Output file length must match source", validPdf.length(), outputFile.length())

            // Verify the file is a genuinely valid PDF that can be parsed
            val loadedDoc = PDDocument.load(outputFile)
            assertNotNull("Loaded document must not be null", loadedDoc)
            assertEquals(1, loadedDoc.numberOfPages)
            assertEquals("Save Verification Document", loadedDoc.documentInformation.title)
            loadedDoc.close()

            // Verify ViewModel state
            assertEquals(destUri, viewModel.lastDecryptedUri.value)
            assertFalse(viewModel.isProcessing.value)
            val expectedSuccess = context.getString(R.string.summary_decrypted_saved, 1)
            assertEquals(expectedSuccess, viewModel.statusMessage.value)

            // Verify toast effect
            val effect = awaitItem()
            assertTrue(effect is UiEffect.ShowToast)
            assertEquals(expectedSuccess, (effect as UiEffect.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFromViewer_zeroByteFile_returnsErrorFeedbackAndDoesNotMarkAsDecrypted() = runTest(testDispatcher) {
        val zeroByteFile = File(context.cacheDir, "viewer_zero_byte.pdf")
        zeroByteFile.writeBytes(ByteArray(0))
        tempFiles.add(zeroByteFile)
        val sourceUri = Uri.fromFile(zeroByteFile)

        val outputFile = File(context.cacheDir, "viewer_output_zero.pdf")
        if (outputFile.exists()) outputFile.delete()
        tempFiles.add(outputFile)
        val destUri = Uri.fromFile(outputFile)

        viewModel.uiEffect.test {
            viewModel.copyUriStream(context, sourceUri, destUri)
            advanceUntilIdle()

            // Verify lastDecryptedUri is not set to destUri
            assertNull("lastDecryptedUri must not be set on 0-byte failure", viewModel.lastDecryptedUri.value)
            assertFalse(viewModel.isProcessing.value)

            val expectedError = context.getString(R.string.summary_error, 1)
            assertEquals(expectedError, viewModel.statusMessage.value)

            val effect = awaitItem()
            assertTrue(effect is UiEffect.ShowToast)
            assertEquals(expectedError, (effect as UiEffect.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFromViewer_nullInputStream_returnsErrorWithoutHanging() = runTest(testDispatcher) {
        val customDecryptUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeInputStream(context: Context, uri: Uri): InputStream? = null
        }
        val customVm = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = customDecryptUseCase
        )

        val dummySource = Uri.parse("content://invalid.provider/missing.pdf")
        val dummyDest = Uri.parse("content://invalid.provider/dest.pdf")

        customVm.uiEffect.test {
            customVm.copyUriStream(context, dummySource, dummyDest)
            advanceUntilIdle()

            assertFalse(customVm.isProcessing.value)
            assertNull(customVm.lastDecryptedUri.value)

            val expectedError = context.getString(R.string.summary_error, 1)
            assertEquals(expectedError, customVm.statusMessage.value)

            val effect = awaitItem()
            assertTrue(effect is UiEffect.ShowToast)
            assertEquals(expectedError, (effect as UiEffect.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFromViewer_streamInterruption_handlesGracefullyWithoutHanging() = runTest(testDispatcher) {
        val interruptingUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeInputStream(context: Context, uri: Uri): InputStream {
                return object : InputStream() {
                    override fun read(): Int {
                        throw IOException("I/O pipe broke during preview save")
                    }
                }
            }
        }
        val customVm = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = interruptingUseCase
        )

        val dummySource = Uri.parse("content://interrupted.provider/doc.pdf")
        val dummyDest = Uri.parse("content://interrupted.provider/out.pdf")

        customVm.uiEffect.test {
            customVm.copyUriStream(context, dummySource, dummyDest)
            advanceUntilIdle()

            assertFalse("App must not hang on stream exception", customVm.isProcessing.value)
            assertNull(customVm.lastDecryptedUri.value)

            val expectedError = context.getString(R.string.summary_error, 1)
            assertEquals(expectedError, customVm.statusMessage.value)

            val effect = awaitItem()
            assertTrue(effect is UiEffect.ShowToast)
            assertEquals(expectedError, (effect as UiEffect.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveFromViewer_nullOutputStream_returnsErrorWithoutHanging() = runTest(testDispatcher) {
        val validPdf = createValidPdf("viewer_preview_valid_out_null")
        val sourceUri = Uri.fromFile(validPdf)

        val nullOutUseCase = object : DecryptPdfUseCase(testDispatcher) {
            override fun openSafeOutputStream(context: Context, uri: Uri): OutputStream? = null
        }
        val customVm = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            decryptPdfUseCase = nullOutUseCase
        )

        val dummyDest = Uri.parse("content://saf/denied_output.pdf")

        customVm.uiEffect.test {
            customVm.copyUriStream(context, sourceUri, dummyDest)
            advanceUntilIdle()

            assertFalse(customVm.isProcessing.value)
            assertNull(customVm.lastDecryptedUri.value)

            val expectedError = context.getString(R.string.summary_error, 1)
            assertEquals(expectedError, customVm.statusMessage.value)

            val effect = awaitItem()
            assertTrue(effect is UiEffect.ShowToast)
            assertEquals(expectedError, (effect as UiEffect.ShowToast).message)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
