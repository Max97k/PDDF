package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileUtilsTest {

    @Test
    fun testGetFileName_fileScheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("file:///storage/emulated/0/Download/sample_document.pdf")

        val fileName = FileUtils.getFileName(context, uri)
        assertEquals("sample_document.pdf", fileName)
    }

    @Test
    fun testGetFileName_unknownPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/docs/report.pdf")

        val fileName = FileUtils.getFileName(context, uri)
        assertEquals("report.pdf", fileName)
    }
}
