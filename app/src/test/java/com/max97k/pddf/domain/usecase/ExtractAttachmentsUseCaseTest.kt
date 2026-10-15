package com.max97k.pddf.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ExtractAttachmentsUseCaseTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createPdfWithAttachment(): File {
        val file = File(context.cacheDir, "with_attachment.pdf")
        val doc = PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage()
        doc.addPage(page)

        val names = PDDocumentNameDictionary(doc.documentCatalog)
        val efTree = PDEmbeddedFilesNameTreeNode()

        val fs = PDComplexFileSpecification()
        fs.file = "test.txt"

        val ef = PDEmbeddedFile(doc, ByteArrayInputStream("Hello".toByteArray()))
        ef.subtype = "text/plain"
        ef.size = 5
        fs.embeddedFile = ef

        val map = mapOf("test.txt" to fs)
        efTree.names = map
        names.embeddedFiles = efTree
        doc.documentCatalog.names = names

        val os = FileOutputStream(file)
        doc.save(os)
        os.close()
        doc.close()

        return file
    }

    private fun createPdfWithoutAttachment(): File {
        val file = File(context.cacheDir, "without_attachment.pdf")
        val doc = PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        val os = FileOutputStream(file)
        doc.save(os)
        os.close()
        doc.close()
        return file
    }

    @Test
    fun testGetAttachments_withAttachments() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val pdfRepository = PdfRepositoryImpl(context, testDispatcher)
        val useCase = ExtractAttachmentsUseCase(pdfRepository, testDispatcher)

        val file = createPdfWithAttachment()
        val attachments = useCase.getAttachments("file://" + file.absolutePath)
        assertEquals(1, attachments.size)
        assertEquals("test.txt", attachments[0].name)
        assertEquals(5L, attachments[0].sizeBytes)
        assertEquals("text/plain", attachments[0].mimeType)
    }

    @Test
    fun testGetAttachments_withoutAttachments() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val pdfRepository = PdfRepositoryImpl(context, testDispatcher)
        val useCase = ExtractAttachmentsUseCase(pdfRepository, testDispatcher)

        val file = createPdfWithoutAttachment()
        val attachments = useCase.getAttachments("file://" + file.absolutePath)
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun testSaveAttachment() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val pdfRepository = PdfRepositoryImpl(context, testDispatcher)
        val useCase = ExtractAttachmentsUseCase(pdfRepository, testDispatcher)

        val file = createPdfWithAttachment()
        val destFile = File(context.cacheDir, "saved_test.txt")
        val success = useCase.saveAttachment("file://" + file.absolutePath, "test.txt", "file://" + destFile.absolutePath)
        assertTrue(success)
        assertTrue(destFile.exists())
        assertEquals("Hello", destFile.readText())
    }
}
