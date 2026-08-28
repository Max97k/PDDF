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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [36])
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
            .build()
        repository = PasswordRepository(database.passwordDao(), com.example.util.CryptoManager())
        viewModel = MainViewModel(application, repository)
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
    fun testUpdateConflictSettings() {
        viewModel.updateConflictSettings(ConflictMode.OVERWRITE, true)

        assertEquals(ConflictMode.OVERWRITE, viewModel.conflictMode.value)
        assertTrue(viewModel.rememberConflictChoice.value)

        // Verify persistence in another ViewModel instance
        val newViewModel = MainViewModel(application, repository)
        assertEquals(ConflictMode.OVERWRITE, newViewModel.conflictMode.value)
        assertTrue(newViewModel.rememberConflictChoice.value)

        // Toggle back off
        viewModel.updateConflictSettings(ConflictMode.SAVE_AS_COPY, false)
        assertFalse(viewModel.rememberConflictChoice.value)
    }

    @Test
    fun testSaveAndDeletePassword() = runTest {
        viewModel.savePassword("Tax Return", "pass123")
        advanceUntilIdle()

        var passwordsResult = repository.allPasswords.first()
        assertTrue(passwordsResult is com.example.util.Result.Success)
        var passwords = (passwordsResult as com.example.util.Result.Success).data
        assertTrue(passwords.any { it.name == "Tax Return" && it.passwordValue == "pass123" })

        val savedEntity = passwords.first { it.name == "Tax Return" }
        viewModel.deletePassword(savedEntity.id)
        advanceUntilIdle()

        passwordsResult = repository.allPasswords.first()
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
}
