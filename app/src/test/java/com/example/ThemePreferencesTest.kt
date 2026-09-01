package com.example

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_theme_prefs.preferences_pb") }
        )
        val application = ApplicationProvider.getApplicationContext<Application>()
        themePreferences = ThemePreferences(application, testDataStore)
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
