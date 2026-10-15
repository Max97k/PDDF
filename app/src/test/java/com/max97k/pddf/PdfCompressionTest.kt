package com.max97k.pddf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfCompressionTest {

    private lateinit var context: Context
    private lateinit var pdfRepository: PdfRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        // Wait, standard test dispatcher doesn't work if initialized outside runTest unless using rule, we can initialize it inside runTest
    }

    @Test
    fun compressPdf_reducesFileSize() = runTest {
        pdfRepository = PdfRepositoryImpl(context, StandardTestDispatcher(testScheduler))
        val originalFile = File(context.cacheDir, "original.pdf")
        val compressedFile = File(context.cacheDir, "compressed.pdf")

        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            // Add metadata
            val catalog = doc.documentCatalog
            val metadataStream = doc.document.createCOSStream()
            metadataStream.createOutputStream().use { it.write("Fake Metadata".toByteArray()) }
            val metaDict = com.tom_roush.pdfbox.pdmodel.common.PDMetadata(metadataStream)
            catalog.metadata = metaDict

            // Add uncompressed text (by not specifying compression on ContentStream)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(100f, 700f)
                cs.showText("This is a test of PDF compression." + "A".repeat(1000))
                cs.endText()
            }

            // Add unreferenced object manually
            val unreferenced = doc.document.createCOSStream()
            unreferenced.createOutputStream().use { it.write("Unreferenced dummy data".toByteArray()) }

            doc.save(originalFile)
        }

        val originalSize = originalFile.length()

        val result = pdfRepository.compressPdf(originalFile.absolutePath, compressedFile.absolutePath)

        assertTrue(result is com.max97k.pddf.util.Result.Success)

        val compressedSize = compressedFile.length()
        assertTrue("Compressed size ($compressedSize) should be less than original size ($originalSize)", compressedSize < originalSize)

        val reduction = (result as com.max97k.pddf.util.Result.Success<Long>).data
        assertTrue("Reduction ($reduction) should be > 0", reduction > 0)
    }
}
