package com.max97k.pddf.feature.viewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.max97k.pddf.DecryptStatus
import com.max97k.pddf.PdfMetadata
import com.max97k.pddf.domain.repository.PdfEncryptionCheck
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream

class FakePdfRepository : PdfRepository {
    override suspend fun extractPageText(sourceUri: String, page: Int): String? {
        if (page > 3) return null
        return "Page $page text"
    }

    override suspend fun getPageCount(sourceUri: String): Int = 3

    // Stubs
    override suspend fun checkEncryption(sourceUri: String): PdfEncryptionCheck = PdfEncryptionCheck.NotEncrypted
    override suspend fun decrypt(sourceUri: String, destUri: String, passwordChars: CharArray): DecryptStatus = DecryptStatus.SUCCESS
    override suspend fun decrypt(sourceUri: String, destUri: String, passwordValue: String): DecryptStatus = DecryptStatus.SUCCESS
    override suspend fun extractMetadata(sourceUri: String, passwordValue: String): PdfMetadata? = null
    override suspend fun copy(sourceUri: String, destUri: String): Boolean = true
    override suspend fun createTempFile(prefix: String, suffix: String): String = ""
    override suspend fun getShareableUri(fileUri: String): String = ""
    override suspend fun deleteFile(filePathOrUri: String): Boolean = true
    override suspend fun getFileName(uriString: String): String = ""
    override suspend fun createDocumentInDirectory(directoryUriString: String, displayName: String, mimeType: String): String? = null
    override suspend fun findDocumentInDirectory(directoryUriString: String, displayName: String): String? = null
    override suspend fun deleteDocumentInDirectory(directoryUriString: String, displayName: String): Boolean = true
    override fun openSafeInputStream(uriString: String): InputStream? = null
    override fun openSafeOutputStream(uriString: String): OutputStream? = null
    override suspend fun exportPageAsImage(sourceUri: String, pageIndex: Int, outputUri: String, format: android.graphics.Bitmap.CompressFormat, quality: Int): Boolean = true
    override suspend fun compressPdf(sourceUri: String, destUri: String): com.max97k.pddf.util.Result<Long> = com.max97k.pddf.util.Result.Success(0L)
    override suspend fun getOutline(sourceUri: String, passwordValue: String): List<com.max97k.pddf.feature.viewer.OutlineItem> = emptyList()
    override suspend fun addWatermark(sourceUri: String, destUri: String, text: String, opacity: Float, fontSize: Float): Boolean = true
    override suspend fun extractPages(sourceUri: String, destUri: String, pageRange: String): kotlin.Result<Int> = kotlin.Result.success(1)
    override suspend fun packageToZip(fileUris: List<String>, outputZipUri: String, onProgress: (Int, Int) -> Unit): Boolean = true
    override suspend fun rotatePages(sourceUri: String, destUri: String, pageIndices: List<Int>?, rotationDegrees: Int): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PdfTextToSpeechControllerTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope
    private lateinit var controller: PdfTextToSpeechController
    private lateinit var fakeRepository: FakePdfRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope(UnconfinedTestDispatcher())
        fakeRepository = FakePdfRepository()
        controller = PdfTextToSpeechController(context, fakeRepository, testScope)
    }

    @Test
    fun testPlayInitializesState() = testScope.runTest {
        controller.play("content://test", 2)
        controller.currentPage.test {
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        controller.totalPages.test {
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNextPage() = testScope.runTest {
        controller.play("content://test", 1)
        controller.nextPage()

        controller.currentPage.test {
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testPreviousPage() = testScope.runTest {
        controller.play("content://test", 3)
        controller.previousPage()

        controller.currentPage.test {
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSetRate() = testScope.runTest {
        controller.playbackRate.test {
            assertEquals(1.0f, awaitItem())
            controller.setRate(1.5f)
            assertEquals(1.5f, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
