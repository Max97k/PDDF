package com.example

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboCursor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun getFileName_fileScheme_returnsFileName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("file:///storage/emulated/0/Download/my_invoice.pdf")

        val result = getFileName(context, uri)

        assertEquals("my_invoice.pdf", result)
    }

    @Test
    fun getFileName_contentScheme_withCursor_returnsDisplayName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/document/1")

        val cursor = RoboCursor()
        cursor.setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
        cursor.setResults(arrayOf(arrayOf("bank_statement.pdf")))

        shadowOf(context.contentResolver).setCursor(uri, cursor)

        val result = getFileName(context, uri)

        assertEquals("bank_statement.pdf", result)
    }

    @Test
    fun getFileName_contentScheme_emptyCursor_fallsBackToPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/document/fallback_file.pdf")

        val cursor = RoboCursor()
        cursor.setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
        // No results
        cursor.setResults(arrayOf())

        shadowOf(context.contentResolver).setCursor(uri, cursor)

        val result = getFileName(context, uri)

        assertEquals("fallback_file.pdf", result)
    }

    @Test
    fun getFileName_contentScheme_noCursor_fallsBackToPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.example.provider/document/another_file.pdf")

        val result = getFileName(context, uri)

        assertEquals("another_file.pdf", result)
    }

    @Test
    fun getFileName_nullPath_returnsUnknown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A URI with a scheme but no path will return "Unknown" as fallback
        val uri = Uri.parse("mailto:someone@example.com")

        val result = getFileName(context, uri)

        assertEquals("Unknown", result)
    }

    @Test
    fun getFileName_pathWithoutSlash_returnsPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("just_a_name.pdf")

        val result = getFileName(context, uri)

        assertEquals("just_a_name.pdf", result)
    }
}
