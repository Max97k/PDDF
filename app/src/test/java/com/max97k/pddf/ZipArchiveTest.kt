package com.max97k.pddf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.repository.PdfRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class ZipArchiveTest {

    private lateinit var context: Context
    private lateinit var pdfRepository: PdfRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pdfRepository = PdfRepositoryImpl(context, Dispatchers.Unconfined)
    }

    @Test
    fun `test packageToZip creates zip file successfully`() = runBlocking {
        // Create dummy files
        val file1 = File(context.cacheDir, "file1.pdf")
        val file2 = File(context.cacheDir, "file2.pdf")

        FileOutputStream(file1).use { it.write("Dummy PDF content 1".toByteArray()) }
        FileOutputStream(file2).use { it.write("Dummy PDF content 2".toByteArray()) }

        val outputZip = File(context.cacheDir, "output.zip")

        val result = pdfRepository.packageToZip(
            fileUris = listOf(file1.absolutePath, file2.absolutePath),
            outputZipUri = outputZip.absolutePath
        ) { _, _ -> }

        assertTrue(result)
        assertTrue(outputZip.exists())

        ZipFile(outputZip).use { zip ->
            assertEquals(2, zip.size())
            val entries = zip.entries().toList()
            assertTrue(entries.any { it.name == "file1.pdf" })
            assertTrue(entries.any { it.name == "file2.pdf" })

            // verify content
            val entry1 = zip.getEntry("file1.pdf")
            val content1 = zip.getInputStream(entry1).bufferedReader().readText()
            assertEquals("Dummy PDF content 1", content1)
        }
    }
}
