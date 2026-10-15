package com.max97k.pddf

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.ReadingMode
import com.max97k.pddf.data.SecurityPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityPreferencesTest {

    private lateinit var application: Application

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDefaults() = runTest {
        val prefs = SecurityPreferences(application, UnconfinedTestDispatcher())
        assertEquals(false, prefs.screenshotProtection.first())
        assertEquals(ReadingMode.LIGHT, prefs.readingMode.first())
    }

    @Test
    fun testSaveAndReadReadingMode() = runTest {
        val prefs = SecurityPreferences(application, UnconfinedTestDispatcher())

        prefs.saveReadingMode(ReadingMode.SEPIA)
        assertEquals(ReadingMode.SEPIA, prefs.readingMode.first())

        prefs.saveReadingMode(ReadingMode.E_INK)
        assertEquals(ReadingMode.E_INK, prefs.readingMode.first())
    }
}
