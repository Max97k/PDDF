package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.io.File
import org.robolectric.shadows.ShadowContentResolver
import java.io.FileOutputStream
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DecryptPdfTest {

    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel

    private lateinit var encryptedUri: Uri
    private lateinit var unencryptedUri: Uri
    private lateinit var outputUri: Uri
    private lateinit var invalidPasswordUri: Uri

    private lateinit var outputFile: File

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        PDFBoxResourceLoader.init(context)
        viewModel = MainViewModel(app)

        outputFile = File.createTempFile("output", ".pdf")

        val encryptedFile = File("src/test/assets/encrypted.pdf")
        val unencryptedFile = File("src/test/assets/unencrypted.pdf")

        encryptedUri = Uri.fromFile(encryptedFile)
        unencryptedUri = Uri.fromFile(unencryptedFile)
        invalidPasswordUri = Uri.fromFile(encryptedFile)
        outputUri = Uri.fromFile(outputFile)
    }

    @After
    fun teardown() {
        outputFile.delete()
    }

    @Test
    fun testDecryptPdf_withCorrectPassword() {
        val result = viewModel.decryptSinglePdf(context, encryptedUri, outputUri, "password")

        assertEquals(DecryptStatus.SUCCESS, result)
        assertTrue("Output file should not be empty", outputFile.length() > 0)
    }

    @Test
    fun testDecryptPdf_withWrongPassword() {
        val result = viewModel.decryptSinglePdf(context, invalidPasswordUri, outputUri, "wrongpassword")
        assertEquals(DecryptStatus.WRONG_PASSWORD, result)
    }

    @Test
    fun testDecryptPdf_withUnencryptedPdf() {
        val result = viewModel.decryptSinglePdf(context, unencryptedUri, outputUri, "")
        assertEquals(DecryptStatus.NOT_ENCRYPTED, result)
    }
}
