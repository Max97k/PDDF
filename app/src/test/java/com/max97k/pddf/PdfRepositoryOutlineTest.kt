package com.max97k.pddf

import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.max97k.pddf.feature.viewer.OutlineItem
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers

@RunWith(RobolectricTestRunner::class)
class PdfRepositoryOutlineTest {

    private lateinit var repository: PdfRepositoryImpl
    private lateinit var testFile: File

    @Before
    fun setup() {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        repository = PdfRepositoryImpl(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun teardown() {
        if (::testFile.isInitialized && testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun getOutline_returnsCorrectHierarchy() = runTest {
        val document = PDDocument()
        val page1 = PDPage()
        val page2 = PDPage()
        document.addPage(page1)
        document.addPage(page2)

        val documentOutline = PDDocumentOutline()
        document.documentCatalog.documentOutline = documentOutline

        val rootItem1 = PDOutlineItem()
        rootItem1.title = "Chapter 1"
        rootItem1.destination = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination().apply { page = page1 }

        val childItem1 = PDOutlineItem()
        childItem1.title = "Section 1.1"
        childItem1.destination = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination().apply { page = page2 }

        rootItem1.addLast(childItem1)
        documentOutline.addLast(rootItem1)

        testFile = File.createTempFile("test_outline", ".pdf")
        document.save(testFile)
        document.close()

        val outline = repository.getOutline(testFile.absolutePath, "")

        assertEquals(1, outline.size)

        val firstRoot = outline[0]
        assertEquals("Chapter 1", firstRoot.title)
        assertEquals(0, firstRoot.pageIndex)
        assertEquals(1, firstRoot.children.size)

        val firstChild = firstRoot.children[0]
        assertEquals("Section 1.1", firstChild.title)
        assertEquals(1, firstChild.pageIndex)
    }
}
