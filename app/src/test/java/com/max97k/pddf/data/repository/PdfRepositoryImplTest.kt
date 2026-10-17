package com.max97k.pddf.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import com.max97k.pddf.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfRepositoryImplTest {

    private lateinit var application: Application
    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var pdfRepository: PdfRepository

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(application)
        testDispatcher = StandardTestDispatcher()
        pdfRepository = PdfRepositoryImpl(application, testDispatcher)
    }

    @Test
    fun testReEncryptPdf_Success() = runTest(testDispatcher) {
        val srcFile = File(application.cacheDir, "test_plain.pdf")
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            doc.save(srcFile)
        }

        val destFile = File(application.cacheDir, "test_protected.pdf")
        if (destFile.exists()) {
            destFile.delete()
        }

        val password = "secretPassword".toCharArray()

        // Let's use `srcFile.toURI().toString()`
        val sourceUri = "file://" + srcFile.absolutePath
        val destUri = "file://" + destFile.absolutePath
        val result = pdfRepository.reEncrypt(sourceUri, destUri, password)

        if (result.isFailure) throw result.exceptionOrNull()!!
        assertTrue(result.isSuccess)
        val encryptionCheck = pdfRepository.checkEncryption(destUri)
        assertEquals(PdfEncryptionCheck.Encrypted, encryptionCheck)
    }
}
