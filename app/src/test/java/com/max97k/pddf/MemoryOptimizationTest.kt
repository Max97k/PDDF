package com.max97k.pddf

import com.max97k.pddf.util.MemoryUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.content.ComponentCallbacks2

@RunWith(RobolectricTestRunner::class)
class MemoryOptimizationTest {

    @Test
    fun testOptimalMemorySetting() {
        val smallFileSize = 10L * 1024 * 1024 // 10MB
        val largeFileSize = 60L * 1024 * 1024 // 60MB

        val smallSetting = MemoryUtils.getOptimalMemorySetting(smallFileSize)
        val largeSetting = MemoryUtils.getOptimalMemorySetting(largeFileSize)

        assertTrue("Small files should use main memory", smallSetting.useMainMemory())
        assertFalse("Large files should use temp file only (not main memory)", largeSetting.useMainMemory())
    }

    @Test
    fun testPdfBoxFontCacheClear() {
        // Init PDFBox resources for test context
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        // Just verifying it doesn't throw exceptions when called
        MemoryUtils.clearPdfBoxFontCache()
    }

    @Test
    fun testOnTrimMemory() {
        val app = PdfApplication()
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        app.onLowMemory()
        // verify execution without exceptions
    }
}
