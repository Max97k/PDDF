package com.max97k.pddf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.viewModels
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityTest {

    class TestContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = testCursor
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        companion object {
            var testCursor: Cursor? = null
        }
    }

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
        val uri = Uri.parse("content://com.max97k.pddf.provider.test1/document/1")

        val matrixCursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        matrixCursor.addRow(arrayOf("bank_statement.pdf"))

        TestContentProvider.testCursor = matrixCursor
        Robolectric.setupContentProvider(TestContentProvider::class.java, "com.max97k.pddf.provider.test1")

        val result = getFileName(context, uri)

        assertEquals("bank_statement.pdf", result)
    }

    @Test
    fun getFileName_contentScheme_emptyCursor_fallsBackToPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.max97k.pddf.provider.test2/document/fallback_file.pdf")

        val matrixCursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))

        TestContentProvider.testCursor = matrixCursor
        Robolectric.setupContentProvider(TestContentProvider::class.java, "com.max97k.pddf.provider.test2")

        val result = getFileName(context, uri)

        assertEquals("fallback_file.pdf", result)
    }

    @Test
    fun getFileName_contentScheme_noCursor_fallsBackToPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://com.max97k.pddf.provider/document/another_file.pdf")

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

    @Test
    fun handleIntent_showSavedPasswords_opensPasswordDialog() {
        val intent = android.content.Intent("com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS")
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        val activity = controller.get()
        val viewModel: MainViewModel by activity.viewModels()
        org.junit.Assert.assertTrue(viewModel.showPasswordListDialog.value)
    }

    @Test
    fun handleIntent_selectPdf_triggersDocumentPicker() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val viewModel: MainViewModel by activity.viewModels()
        val intent = android.content.Intent("com.max97k.pddf.ACTION_SELECT_PDF")
        controller.newIntent(intent)
        org.junit.Assert.assertTrue(viewModel.requestOpenDocumentPicker.value)
    }
}
