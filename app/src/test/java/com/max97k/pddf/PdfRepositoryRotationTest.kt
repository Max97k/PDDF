package com.max97k.pddf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfRepositoryRotationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: PdfRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        repository = PdfRepositoryImpl(context, testDispatcher)
    }

    @Test
    fun rotatePages_singlePage_updatesRotation() = runTest(testDispatcher) {
        val srcFile = File.createTempFile("src_", ".pdf")
        val dstFile = File.createTempFile("dst_", ".pdf")

        // Create 2-page PDF
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            doc.save(srcFile)
        }

        val success = repository.rotatePages(
            sourceUri = srcFile.toURI().toString(),
            destUri = dstFile.toURI().toString(),
            pageIndices = listOf(0),
            rotationDegrees = 90
        )

        assertTrue(success)

        PDDocument.load(dstFile).use { doc ->
            assertEquals(90, doc.getPage(0).rotation)
            assertEquals(0, doc.getPage(1).rotation)
        }

        srcFile.delete()
        dstFile.delete()
    }

    @Test
    fun rotatePages_allPages_updatesRotationWithModulo() = runTest(testDispatcher) {
        val srcFile = File.createTempFile("src_", ".pdf")
        val dstFile = File.createTempFile("dst_", ".pdf")

        PDDocument().use { doc ->
            val page1 = PDPage().apply { rotation = 90 }
            val page2 = PDPage().apply { rotation = 270 }
            doc.addPage(page1)
            doc.addPage(page2)
            doc.save(srcFile)
        }

        val success = repository.rotatePages(
            sourceUri = srcFile.toURI().toString(),
            destUri = dstFile.toURI().toString(),
            pageIndices = null,
            rotationDegrees = -90
        )

        assertTrue(success)

        PDDocument.load(dstFile).use { doc ->
            assertEquals(0, doc.getPage(0).rotation)
            assertEquals(180, doc.getPage(1).rotation)
        }

        srcFile.delete()
        dstFile.delete()
    }
}
