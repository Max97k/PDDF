package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.io.File
import java.io.InputStream
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DecryptPdfTest {

    private lateinit var context: Context
    private lateinit var shadowContentResolver: ShadowContentResolver

    private val encryptedUri = Uri.parse("content://test/encrypted.pdf")
    private val unencryptedUri = Uri.parse("content://test/unencrypted.pdf")
    private val outputUri = Uri.parse("content://test/output.pdf")
    private val invalidPasswordUri = Uri.parse("content://test/invalid.pdf")

    private lateinit var outputFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        shadowContentResolver = Shadows.shadowOf(context.contentResolver)

        outputFile = File.createTempFile("output", ".pdf")

        val encryptedFile = File("src/test/assets/encrypted.pdf")
        val unencryptedFile = File("src/test/assets/unencrypted.pdf")

        shadowContentResolver.registerInputStream(encryptedUri, encryptedFile.inputStream())
        shadowContentResolver.registerInputStream(unencryptedUri, unencryptedFile.inputStream())

        shadowContentResolver.registerOutputStream(outputUri, FileOutputStream(outputFile))
    }

    @After
    fun teardown() {
        outputFile.delete()
    }

    @Test
    fun testDecryptPdf_withCorrectPassword() {
        val result = decryptPdf(context, encryptedUri, outputUri, "password")

        assertTrue("Expected PDF decryption to succeed", result)
        assertTrue("Output file should not be empty", outputFile.length() > 0)
    }

    @Test
    fun testDecryptPdf_withWrongPassword() {
        val encryptedFile = File("src/test/assets/encrypted.pdf")
        shadowContentResolver.registerInputStream(invalidPasswordUri, encryptedFile.inputStream())

        var threwException = false
        try {
            decryptPdf(context, invalidPasswordUri, outputUri, "wrongpassword")
        } catch (e: Exception) {
            threwException = true
        }

        assertTrue("Expected an exception for wrong password", threwException)
    }

    @Test
    fun testDecryptPdf_withUnencryptedPdf() {
        val result = decryptPdf(context, unencryptedUri, outputUri, "")
        assertFalse("Expected PDF decryption to fail or skip for unencrypted file", result)
    }
}
