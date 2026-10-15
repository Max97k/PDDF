package com.max97k.pddf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PdfRepositoryImplExportTest {

    private lateinit var context: Context
    private lateinit var pdfRepository: PdfRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        pdfRepository = PdfRepositoryImpl(context, Dispatchers.Unconfined)
    }

    // Since Robolectric's native PdfRenderer shadow fails or throws NoSuchMethodError (getOwnerId$)
    // we just test that the fallback error path doesn't crash when passing an invalid Uri or non-existent file.

    @Test
    fun exportPageAsImage_invalidUri_returnsFalse() = runTest {
        val result = pdfRepository.exportPageAsImage(
            sourceUri = "content://invalid",
            pageIndex = 0,
            outputUri = "content://invalid_out",
            format = Bitmap.CompressFormat.PNG,
            quality = 100
        )
        assertFalse(result)
    }
}
