package com.max97k.pddf.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.domain.repository.PdfRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfRepositoryImplWatermarkTest {
    private lateinit var context: Context
    private lateinit var repository: PdfRepository
    private lateinit var sourceFile: File
    private lateinit var destFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        repository = PdfRepositoryImpl(context)

        sourceFile = File(context.cacheDir, "test_source.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(sourceFile)
        }
        destFile = File(context.cacheDir, "test_dest.pdf")
    }

    @After
    fun teardown() {
        sourceFile.delete()
        destFile.delete()
    }

    @Test
    fun testAddWatermark() = runTest {
        val result = repository.addWatermark(
            sourceFile.absolutePath,
            destFile.absolutePath,
            "TEST WATERMARK",
            0.3f,
            48f
        )
        assertTrue(result)
        assertTrue(destFile.exists())
        assertTrue(destFile.length() > sourceFile.length())

        PDDocument.load(destFile).use { doc ->
            var foundText = false
            var foundRotation = false
            for (page in doc.pages) {
                val parser = PDFStreamParser(page)
                parser.parse()
                val tokens = parser.tokens
                for (token in tokens) {
                    if (token is com.tom_roush.pdfbox.contentstream.operator.Operator) {
                        if (token.name == "cm") {
                            foundRotation = true
                        }
                        if (token.name == "Tj" || token.name == "TJ") {
                            foundText = true
                        }
                    }
                }
            }
            assertTrue("Expected text drawing command in content stream", foundText)
            assertTrue("Expected matrix transformation in content stream", foundRotation)
        }
    }
}
