package com.example

import android.app.Application
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.ThemeMode
import com.example.feature.decrypt.PDFDecryptorScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MultiDeviceScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_phone_pixel8_light() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                PDFDecryptorScreen(
                    viewModel = viewModel,
                    windowWidthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pixel8_light.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun screenshot_phone_pixel8_dark() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                PDFDecryptorScreen(
                    viewModel = viewModel,
                    windowWidthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pixel8_dark.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel4a, sdk = [34])
    fun screenshot_phone_pixel4a_compact() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                PDFDecryptorScreen(
                    viewModel = viewModel,
                    windowWidthSizeClass = WindowWidthSizeClass.Compact
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pixel4a_compact.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.PixelFold, sdk = [34])
    fun screenshot_foldable_pixel_fold() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                PDFDecryptorScreen(
                    viewModel = viewModel,
                    windowWidthSizeClass = WindowWidthSizeClass.Medium
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pixel_fold_medium.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.PixelTablet, sdk = [34])
    fun screenshot_tablet_pixel_tablet() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                PDFDecryptorScreen(
                    viewModel = viewModel,
                    windowWidthSizeClass = WindowWidthSizeClass.Expanded
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/pixel_tablet_expanded.png")
    }
}
