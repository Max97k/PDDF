package com.max97k.pddf.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
class FlattenPdfAnnotationsUseCaseTest {

    private lateinit var useCase: FlattenPdfAnnotationsUseCase
    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)

        testDispatcher = StandardTestDispatcher()

        val fakeRepository = object : PdfRepository {
            override suspend fun checkEncryption(sourceUri: String) = TODO()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray) = TODO()
            override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String) = TODO()
            override suspend fun extractMetadata(sourceUri: String, passwordValue: String) = TODO()
            override suspend fun copy(sourceUri: String, destUri: String) = TODO()
            override suspend fun createTempFile(prefix: String, suffix: String) = TODO()
            override suspend fun getShareableUri(fileUri: String) = TODO()
            override suspend fun deleteFile(filePathOrUri: String) = TODO()
            override suspend fun getFileName(uriString: String) = TODO()
            override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String) = TODO()
            override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String) = TODO()
            override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String) = TODO()
            override suspend fun addWatermark(sourceUri: String, destUri: String, text: String, opacity: Float, fontSize: Float) = TODO()

            override fun openSafeInputStream(uriString: String): InputStream? {
                return FileInputStream(File(uriString))
            }

            override fun openSafeOutputStream(uriString: String): OutputStream? {
                return FileOutputStream(File(uriString))
            }
        }

        useCase = FlattenPdfAnnotationsUseCase(fakeRepository, testDispatcher)
    }

    @Test
    fun `flattenForms removes editable fields from acroForm`() = runTest(testDispatcher) {
        val inputPdf = File(context.cacheDir, "input_form.pdf")
        val outputPdf = File(context.cacheDir, "output_flat.pdf")

        // Create a PDF with a form field
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm

            val textBox = PDTextField(acroForm)
            textBox.partialName = "SampleField"
            acroForm.fields.add(textBox)

            doc.save(inputPdf)
        }

        // Run the use case
        val result = useCase.flattenForms(inputPdf.absolutePath, outputPdf.absolutePath)

        assertTrue(result)
        assertTrue(outputPdf.exists())

        // Verify the resulting PDF has no editable fields
        PDDocument.load(outputPdf).use { doc ->
            val form = doc.documentCatalog.acroForm
            val isFlattened = form == null || form.fields.isEmpty()
            assertTrue("AcroForm should have no fields after flattening", isFlattened)
        }
    }
}
