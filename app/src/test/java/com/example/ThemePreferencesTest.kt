package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferencesTest {

    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setup() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        themePreferences = ThemePreferences(application)
    }

    @Test
    fun testSaveAndReadThemeMode() = runTest {
        themePreferences.saveThemeMode(ThemeMode.AMOLED)
        val mode = themePreferences.themeMode.first()
        assertEquals(ThemeMode.AMOLED, mode)

        themePreferences.saveThemeMode(ThemeMode.LIGHT)
        val lightMode = themePreferences.themeMode.first()
        assertEquals(ThemeMode.LIGHT, lightMode)
    }
}
