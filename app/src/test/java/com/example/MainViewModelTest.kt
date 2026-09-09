package com.example

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PasswordRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: PasswordRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val keyGen = javax.crypto.KeyGenerator.getInstance("AES")
        keyGen.init(128)
        com.example.util.CryptoManager.testKeyOverride = keyGen.generateKey()

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        repository = PasswordRepository(database.passwordDao(), com.example.util.CryptoManager(), ioDispatcher = testDispatcher)
        viewModel = MainViewModel(application, repository, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertEquals(ConflictMode.SAVE_AS_COPY, viewModel.conflictMode.value)
        assertFalse(viewModel.rememberConflictChoice.value)
        assertTrue(viewModel.selectedUris.value.isEmpty())
        assertFalse(viewModel.isProcessing.value)
        assertNull(viewModel.statusMessage.value)
    }

    @Test
    fun testUpdateConflictSettings() = runTest(testDispatcher) {
        viewModel.updateConflictSettings(ConflictMode.OVERWRITE, true)
        advanceUntilIdle()

        assertEquals(ConflictMode.OVERWRITE, viewModel.conflictMode.value)
        assertTrue(viewModel.rememberConflictChoice.value)

        // Verify persistence in another ViewModel instance
        val newViewModel = MainViewModel(application, repository, ioDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals(ConflictMode.OVERWRITE, newViewModel.conflictMode.value)
        assertTrue(newViewModel.rememberConflictChoice.value)

        // Toggle back off
        viewModel.updateConflictSettings(ConflictMode.SAVE_AS_COPY, false)
        advanceUntilIdle()
        assertFalse(viewModel.rememberConflictChoice.value)
    }

    @Test
    fun testSaveAndDeletePassword() = runTest(testDispatcher) {
        viewModel.savePassword("Tax Return", "pass123")
        advanceUntilIdle()

        var passwordsResult = repository.allPasswords.first { it is com.example.util.Result.Success && it.data.isNotEmpty() }
        assertTrue(passwordsResult is com.example.util.Result.Success)
        var passwords = (passwordsResult as com.example.util.Result.Success).data
        assertTrue(passwords.any { it.name == "Tax Return" && it.passwordValue == "pass123" })

        val savedEntity = passwords.first { it.name == "Tax Return" }
        viewModel.deletePassword(savedEntity.id)
        advanceUntilIdle()

        passwordsResult = repository.allPasswords.first { it is com.example.util.Result.Success && it.data.none { p -> p.id == savedEntity.id } }
        passwords = (passwordsResult as com.example.util.Result.Success).data
        assertFalse(passwords.any { it.id == savedEntity.id })
    }

    @Test
    fun testSetSelectedUris_filtersPdfOnly() = runTest {
        val pdfUri = Uri.parse("file:///storage/emulated/0/Download/document.pdf")
        val txtUri = Uri.parse("file:///storage/emulated/0/Download/document.txt")

        viewModel.setSelectedUris(application, listOf(pdfUri, txtUri))
        advanceUntilIdle()

        assertEquals(1, viewModel.selectedUris.value.size)
        assertEquals(pdfUri, viewModel.selectedUris.value[0])
        assertEquals(1, viewModel.selectedFileNames.value.size)
        assertEquals("document.pdf", viewModel.selectedFileNames.value[0])
    }

    @Test
    fun testDecryptSinglePdf_notEncrypted() = runTest {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val inputFile = File.createTempFile("unencrypted", ".pdf", application.cacheDir)
        doc.save(inputFile)
        doc.close()

        val outputFile = File.createTempFile("output", ".pdf", application.cacheDir)

        val status = viewModel.decryptSinglePdf(
            application,
            Uri.fromFile(inputFile),
            Uri.fromFile(outputFile),
            "password"
        )

        assertEquals(DecryptStatus.NOT_ENCRYPTED, status)
    }

    @Test
    fun testDecryptSinglePdf_encryptedAndCorrectPassword() = runTest {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = AccessPermission()
        val standardProtectionPolicy = StandardProtectionPolicy("owner123", "user123", ap)
        standardProtectionPolicy.encryptionKeyLength = 128
        doc.protect(standardProtectionPolicy)

        val inputFile = File.createTempFile("encrypted", ".pdf", application.cacheDir)
        doc.save(inputFile)
        doc.close()

        val outputFile = File.createTempFile("output_decrypted", ".pdf", application.cacheDir)

        val status = viewModel.decryptSinglePdf(
            application,
            Uri.fromFile(inputFile),
            Uri.fromFile(outputFile),
            "user123"
        )

        assertEquals(DecryptStatus.SUCCESS, status)
    }

    @Test
    fun testDecryptSinglePdf_wrongPassword() = runTest {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = AccessPermission()
        val standardProtectionPolicy = StandardProtectionPolicy("owner123", "secretPass", ap)
        standardProtectionPolicy.encryptionKeyLength = 128
        doc.protect(standardProtectionPolicy)

        val inputFile = File.createTempFile("encrypted_wrong", ".pdf", application.cacheDir)
        doc.save(inputFile)
        doc.close()

        val outputFile = File.createTempFile("output_wrong", ".pdf", application.cacheDir)

        val status = viewModel.decryptSinglePdf(
            application,
            Uri.fromFile(inputFile),
            Uri.fromFile(outputFile),
            "wrongPass"
        )

        assertEquals(DecryptStatus.WRONG_PASSWORD, status)
    }

    @Test
    fun testEnumValues() {
        assertEquals(5, DecryptStatus.entries.size)
        assertTrue(DecryptStatus.entries.contains(DecryptStatus.SUCCESS))
        assertTrue(DecryptStatus.entries.contains(DecryptStatus.NOT_ENCRYPTED))
        assertTrue(DecryptStatus.entries.contains(DecryptStatus.WRONG_PASSWORD))
        assertTrue(DecryptStatus.entries.contains(DecryptStatus.UNSUPPORTED_ENCRYPTION))
        assertTrue(DecryptStatus.entries.contains(DecryptStatus.ERROR))

        assertEquals(2, ConflictMode.entries.size)
        assertTrue(ConflictMode.entries.contains(ConflictMode.OVERWRITE))
        assertTrue(ConflictMode.entries.contains(ConflictMode.SAVE_AS_COPY))
    }

    @Test
    fun testTriggerOpenDocumentPicker() {
        assertFalse(viewModel.requestOpenDocumentPicker.value)
        viewModel.triggerOpenDocumentPicker()
        assertTrue(viewModel.requestOpenDocumentPicker.value)
        viewModel.onDocumentPickerLaunched()
        assertFalse(viewModel.requestOpenDocumentPicker.value)
    }

    @Test
    fun testHandleExternalPdfIntent_corruptedPdf_showsSnackbarAndDoesNotShowPrompt() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val corruptFile = File.createTempFile("corrupt_intent", ".pdf", application.cacheDir)
        corruptFile.writeBytes("CORRUPTED_PDF_HEADER_CONTENT".toByteArray())
        corruptFile.deleteOnExit()

        viewModel.handleExternalPdfIntent(application, Uri.fromFile(corruptFile))
        advanceUntilIdle()

        assertFalse(viewModel.showAutoUnlockPasswordPrompt.value)
        assertFalse(viewModel.isAutoUnlocking.value)
        assertFalse(viewModel.uiState.value.showAutoUnlockPasswordPrompt)
        assertFalse(viewModel.uiState.value.isAutoUnlocking)
        val statusMsg = viewModel.statusMessage.value ?: viewModel.uiState.value.statusMessage
        org.junit.Assert.assertNotNull(statusMsg)
        assertTrue(statusMsg!!.contains("Corrupted") || statusMsg.contains("Failed") || statusMsg.contains("Error"))
    }

    @Test
    fun testHandleExternalPdfIntent_unsupportedEncryption_showsSnackbarAndDoesNotShowPrompt() = runTest(testDispatcher) {
        val mockAutoUnlockUseCase = object : com.example.domain.usecase.AutoUnlockUseCase(
            com.example.domain.usecase.DecryptPdfUseCase(testDispatcher),
            com.example.domain.usecase.PasswordVaultUseCase(repository),
            testDispatcher
        ) {
            override suspend fun tryAutoUnlock(context: android.content.Context, uri: Uri): AutoUnlockResult {
                return AutoUnlockResult.Error("Unsupported encryption/DRM")
            }
        }
        val customViewModel = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            autoUnlockUseCase = mockAutoUnlockUseCase
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { customViewModel.uiState.collect() }

        customViewModel.handleExternalPdfIntent(application, Uri.parse("content://dummy/unsupported.pdf"))
        advanceUntilIdle()

        assertFalse(customViewModel.showAutoUnlockPasswordPrompt.value)
        assertFalse(customViewModel.isAutoUnlocking.value)
        assertFalse(customViewModel.uiState.value.showAutoUnlockPasswordPrompt)
        assertFalse(customViewModel.uiState.value.isAutoUnlocking)
        val statusMsg = customViewModel.statusMessage.value ?: customViewModel.uiState.value.statusMessage
        org.junit.Assert.assertNotNull(statusMsg)
        assertTrue(statusMsg!!.contains("Unsupported encryption"))
    }

    @Test
    fun testUnlockWithManualPassword_unsupportedEncryption_dismissesPromptAndShowsSnackbar() = runTest(testDispatcher) {
        val mockAutoUnlockUseCase = object : com.example.domain.usecase.AutoUnlockUseCase(
            com.example.domain.usecase.DecryptPdfUseCase(testDispatcher),
            com.example.domain.usecase.PasswordVaultUseCase(repository),
            testDispatcher
        ) {
            override suspend fun unlockWithManualPassword(
                context: android.content.Context,
                uri: Uri,
                enteredPassword: String,
                rememberPassword: Boolean,
                fileName: String
            ): Pair<DecryptStatus, Uri?> {
                return Pair(DecryptStatus.UNSUPPORTED_ENCRYPTION, null)
            }
        }
        val customViewModel = MainViewModel(
            application = application,
            repository = repository,
            ioDispatcher = testDispatcher,
            autoUnlockUseCase = mockAutoUnlockUseCase
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { customViewModel.uiState.collect() }

        // Set initial state showing prompt
        customViewModel.updateStateForTesting { it.copy(showAutoUnlockPasswordPrompt = true) }
        assertTrue(customViewModel.showAutoUnlockPasswordPrompt.value)

        customViewModel.unlockWithManualPassword(application, Uri.parse("content://dummy/test.pdf"), "password", false)
        advanceUntilIdle()

        assertFalse(customViewModel.showAutoUnlockPasswordPrompt.value)
        assertFalse(customViewModel.uiState.value.showAutoUnlockPasswordPrompt)
        val statusMsg = customViewModel.statusMessage.value ?: customViewModel.uiState.value.statusMessage
        org.junit.Assert.assertNotNull(statusMsg)
        assertTrue(statusMsg!!.contains(application.getString(R.string.summary_unsupported, 1)))
    }

    @Test
    fun testCheckSelectedPdfs_corruptedFile_recordsWarningInStatusMessage() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val corruptFile = File.createTempFile("corrupt_check", ".pdf", application.cacheDir)
        corruptFile.writeBytes("INVALID_PDF_BYTES".toByteArray())
        corruptFile.deleteOnExit()

        viewModel.setSelectedUris(application, listOf(Uri.fromFile(corruptFile)))
        advanceUntilIdle()

        val statusMsg = viewModel.statusMessage.value ?: viewModel.uiState.value.statusMessage
        org.junit.Assert.assertNotNull(statusMsg)
        assertTrue(statusMsg!!.contains(corruptFile.name))
        assertTrue(statusMsg.contains("corrupted") || statusMsg.contains("unreadable"))
    }

    @Test
    fun testSelectFiles_singleEncryptedPdf_triggersAutoUnlockPrompt() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
        val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("secret", "secret", ap)
        spp.encryptionKeyLength = 128
        doc.protect(spp)
        val inputFile = File.createTempFile("test_locked", ".pdf", application.cacheDir)
        doc.save(inputFile)
        doc.close()

        viewModel.onAction(MainUiAction.SelectFiles(application, listOf(Uri.fromFile(inputFile))))
        advanceUntilIdle()

        assertTrue(viewModel.showAutoUnlockPasswordPrompt.value)
        assertTrue(viewModel.uiState.value.showAutoUnlockPasswordPrompt)
        assertEquals(inputFile.name, viewModel.uiState.value.autoUnlockFileName)
    }

    @Test
    fun testDecryptAndPreview_action_decryptsAndSetsPreviewUri() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        val doc = PDDocument()
        doc.addPage(PDPage())
        val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
        val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("secret", "secret", ap)
        spp.encryptionKeyLength = 128
        doc.protect(spp)
        val inputFile = File.createTempFile("test_locked2", ".pdf", application.cacheDir)
        doc.save(inputFile)
        doc.close()

        val uri = Uri.fromFile(inputFile)
        viewModel.onAction(MainUiAction.DecryptAndPreview(application, uri, "secret"))
        advanceUntilIdle()

        assertNotNull(viewModel.previewPdfUri.value)
        assertNotNull(viewModel.uiState.value.previewPdfUri)
        assertNotNull(viewModel.lastDecryptedUri.value)
    }
}
