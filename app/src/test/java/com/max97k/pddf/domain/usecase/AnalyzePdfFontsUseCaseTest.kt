package com.max97k.pddf.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyzePdfFontsUseCaseTest {

    private lateinit var classUnderTest: AnalyzePdfFontsUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        classUnderTest = AnalyzePdfFontsUseCase()
    }

    @Test
    fun analyze_withStandard14Font_doesNotWarnMissingSystemFonts() {
        val document = PDDocument()
        val page = PDPage()
        val resources = PDResources()
        val font = PDType1Font.HELVETICA
        resources.put(COSName.getPDFName("F1"), font)
        page.resources = resources
        document.addPage(page)

        val result = classUnderTest.analyze(document)

        assertFalse(result.hasMissingNonEmbeddedSystemFonts)
        assertEquals(1, result.fonts.size)
        val extractedFont = result.fonts[0]
        assertEquals(font.name, extractedFont.name)
        assertEquals(font.subType, extractedFont.subType)
        assertFalse(extractedFont.isEmbedded) // standard 14 is not embedded by default
        assertFalse(extractedFont.isSubset)

        document.close()
    }
}
