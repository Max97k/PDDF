package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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

    @Test
    fun testSecureDelete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.cacheDir, "test_shred.pdf")
        testFile.writeText("sensitive pdf content to be shredded")
        assertTrue(testFile.exists())

        val deleted = FileUtils.secureDelete(testFile)
        assertTrue(deleted)
        assertFalse(testFile.exists())
    }

    @Test
    fun testSecureDelete_nullAndNonExistent() {
        assertTrue(FileUtils.secureDelete(null))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nonExistent = File(context.cacheDir, "non_existent_file.pdf")
        assertTrue(FileUtils.secureDelete(nonExistent))
    }

    @Test
    fun testSecureDelete_largeMultiBlockFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.cacheDir, "large_test_shred.pdf")
        // Create 16KB file to test multiple buffer write passes
        val largeBytes = ByteArray(16384) { (it % 256).toByte() }
        testFile.writeBytes(largeBytes)
        assertTrue(testFile.exists())
        assertEquals(16384L, testFile.length())

        val deleted = FileUtils.secureDelete(testFile)
        assertTrue(deleted)
        assertFalse(testFile.exists())
    }
}
