package com.max97k.pddf

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.data.PasswordRepository
import com.max97k.pddf.domain.usecase.AutoUnlockUseCase
import com.max97k.pddf.domain.usecase.BatchProcessUseCase
import com.max97k.pddf.domain.usecase.DecryptPdfUseCase
import com.max97k.pddf.domain.usecase.DomainUseCasesTest
import com.max97k.pddf.domain.usecase.PasswordVaultUseCase
import com.max97k.pddf.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * End-to-end integration test with real PDF documents protected by real encryption algorithms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RealEncryptedPdfIntegrationTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val createdFiles = mutableListOf<File>()

    private val testPassword = "SuperSecretPassword123"
    private val wrongPassword = "IncorrectPassword999"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {}
    }

    @After
    fun tearDown() {
        createdFiles.forEach { FileUtils.secureDelete(it) }
        createdFiles.clear()
    }

    /**
     * Generates a real password-protected PDF document dynamically using PDFBox.
     */
    private fun createRealEncryptedPdf(
        userPassword: String,
        ownerPassword: String = "OwnerSecret999",
        keyLength: Int = 128,
        title: String = "Confidential Bank Statement"
    ): File {
        val file = File(context.cacheDir, "real_encrypted_${System.currentTimeMillis()}_${(0..999).random()}.pdf")
        val doc = PDDocument()
        try {
            doc.documentInformation.title = title
            doc.documentInformation.author = "PDDF Test Suite"

            val page = PDPage()
            doc.addPage(page)

            PDPageContentStream(doc, page).use { cos ->
                cos.beginText()
                cos.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                cos.newLineAtOffset(100f, 700f)
                cos.showText("This is top secret decrypted text.")
                cos.endText()
            }

            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(ownerPassword, userPassword, ap)
            spp.encryptionKeyLength = keyLength
            spp.permissions = ap
            doc.protect(spp)
            doc.save(file)
        } finally {
            doc.close()
        }
        createdFiles.add(file)
        return file
    }

    private fun createUnencryptedPdf(title: String = "Public Report"): File {
        val file = File(context.cacheDir, "unencrypted_${System.currentTimeMillis()}.pdf")
        val doc = PDDocument()
        try {
            doc.documentInformation.title = title
            val page = PDPage()
            doc.addPage(page)
            doc.save(file)
        } finally {
            doc.close()
        }
        createdFiles.add(file)
        return file
    }

    @Test
    fun testDecryptRealPdf_withCorrectPassword_succeedsAndRemovesEncryption() = runTest(testDispatcher) {
        val encryptedFile = createRealEncryptedPdf(userPassword = testPassword)
        val decryptedFile = File(context.cacheDir, "output_decrypted.pdf")
        createdFiles.add(decryptedFile)

        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, testDispatcher)
        val decryptUseCase = DecryptPdfUseCase(pdfRepository, testDispatcher)
        val status = decryptUseCase.decrypt(
            inputUriString = encryptedFile.absolutePath,
            outputUriString = decryptedFile.absolutePath,
            passwordValue = testPassword
        )

        assertEquals("Should return SUCCESS for correct password", DecryptStatus.SUCCESS, status)
        assertTrue("Decrypted file should exist on disk", decryptedFile.exists())
        assertTrue("Decrypted file size should be > 0", decryptedFile.length() > 0)

        // Verify the decrypted file can now be opened WITHOUT any password
        val decryptedDoc = PDDocument.load(decryptedFile)
        assertFalse("Decrypted PDF must no longer be encrypted", decryptedDoc.isEncrypted)
        assertEquals("Document title should be preserved", "Confidential Bank Statement", decryptedDoc.documentInformation.title)
        decryptedDoc.close()
    }

    @Test
    fun testDecryptRealPdf_withWrongPassword_returnsWrongPasswordStatus() = runTest(testDispatcher) {
        val encryptedFile = createRealEncryptedPdf(userPassword = testPassword)
        val decryptedFile = File(context.cacheDir, "output_wrong.pdf")
        createdFiles.add(decryptedFile)

        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, testDispatcher)
        val decryptUseCase = DecryptPdfUseCase(pdfRepository, testDispatcher)
        val status = decryptUseCase.decrypt(
            inputUriString = encryptedFile.absolutePath,
            outputUriString = decryptedFile.absolutePath,
            passwordValue = wrongPassword
        )

        assertEquals("Should return WRONG_PASSWORD for incorrect password", DecryptStatus.WRONG_PASSWORD, status)
    }

    @Test
    fun testDecryptRealPdf_unencryptedFile_returnsNotEncrypted() = runTest(testDispatcher) {
        val plainFile = createUnencryptedPdf()
        val decryptedFile = File(context.cacheDir, "output_plain.pdf")
        createdFiles.add(decryptedFile)

        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, testDispatcher)
        val decryptUseCase = DecryptPdfUseCase(pdfRepository, testDispatcher)
        val status = decryptUseCase.decrypt(
            inputUriString = plainFile.absolutePath,
            outputUriString = decryptedFile.absolutePath,
            passwordValue = testPassword
        )

        assertEquals("Should return NOT_ENCRYPTED for plain files", DecryptStatus.NOT_ENCRYPTED, status)
    }

    @Test
    fun testAutoUnlockUseCase_withMatchingSavedPassword_automaticallyUnlocks() = runTest(testDispatcher) {
        val encryptedFile = createRealEncryptedPdf(userPassword = testPassword)
        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, testDispatcher)
        val decryptUseCase = DecryptPdfUseCase(pdfRepository, testDispatcher)

        val fakeDao = DomainUseCasesTest.FakePasswordDao()
        val repository = PasswordRepository(fakeDao)
        val vaultUseCase = PasswordVaultUseCase(repository)
        vaultUseCase.insertPassword("My Bank Account", testPassword)

        val autoUnlockUseCase = AutoUnlockUseCase(pdfRepository, decryptUseCase, vaultUseCase, testDispatcher)

        val result = autoUnlockUseCase.tryAutoUnlock(encryptedFile.absolutePath)
        assertTrue("Should automatically unlock with matching password", result is AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword)

        val successResult = result as AutoUnlockUseCase.AutoUnlockResult.UnlockedWithSavedPassword
        assertEquals("My Bank Account", successResult.matchedPasswordName)

        val outUri = Uri.parse(successResult.outputUri)
        val decryptedDoc = PDDocument.load(File(outUri.path!!))
        assertFalse("Output document should be unencrypted", decryptedDoc.isEncrypted)
        decryptedDoc.close()
        FileUtils.secureDelete(File(outUri.path!!))
    }

    @Test
    fun testMetadataExtraction_realEncryptedPdf_extractsCorrectInfo() = runTest(testDispatcher) {
        val encryptedFile = createRealEncryptedPdf(userPassword = testPassword, title = "Tax Statement 2026")
        val pdfRepository = com.max97k.pddf.data.repository.PdfRepositoryImpl(context, testDispatcher)
        val decryptUseCase = DecryptPdfUseCase(pdfRepository, testDispatcher)

        val metadata = decryptUseCase.extractMetadata(encryptedFile.absolutePath, testPassword)
        assertNotNull("Metadata should not be null", metadata)
        assertEquals("Tax Statement 2026", metadata!!.title)
        assertEquals(1, metadata.pageCount)
        assertTrue("isEncrypted should be true", metadata.isEncrypted)
    }
}
