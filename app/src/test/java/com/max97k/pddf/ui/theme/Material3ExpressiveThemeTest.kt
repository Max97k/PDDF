package com.max97k.pddf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.max97k.pddf.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class Material3ExpressiveThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun expressiveShapes_areWiredToMaterialTheme() {
        var currentShapes: Shapes? = null
        composeTestRule.setContent {
            MyApplicationTheme {
                currentShapes = MaterialTheme.shapes
            }
        }

        assertNotNull("MaterialTheme.shapes must not be null", currentShapes)
        assertEquals(RoundedCornerShape(4.dp), currentShapes?.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), currentShapes?.small)
        assertEquals(RoundedCornerShape(12.dp), currentShapes?.medium)
        assertEquals(RoundedCornerShape(20.dp), currentShapes?.large)
        assertEquals(RoundedCornerShape(28.dp), currentShapes?.extraLarge)
    }

    @Test
    fun shapeConstantObject_matchesSpecification() {
        assertEquals(RoundedCornerShape(4.dp), Shapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), Shapes.small)
        assertEquals(RoundedCornerShape(12.dp), Shapes.medium)
        assertEquals(RoundedCornerShape(20.dp), Shapes.large)
        assertEquals(RoundedCornerShape(28.dp), Shapes.extraLarge)
    }

    @Test
    fun lightColorScheme_definesExpectedTokensAndContainerTiers() {
        var scheme: ColorScheme? = null
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }

        assertNotNull(scheme)
        assertEquals(Color(0xFFC3C7CF), scheme?.outlineVariant)
        assertEquals(Color(0xFFD8DAE0), scheme?.surfaceDim)
        assertEquals(Color(0xFFFDFBFF), scheme?.surfaceBright)
        assertEquals(md_theme_light_primary, scheme?.surfaceTint)
        assertEquals(Color(0xFF2F3033), scheme?.inverseSurface)
        assertEquals(Color(0xFFF1F0F4), scheme?.inverseOnSurface)
        assertEquals(Color(0xFF9CCAFF), scheme?.inversePrimary)
        assertEquals(Color(0xFF000000), scheme?.scrim)
        assertNotEquals(scheme?.surfaceContainerLowest, scheme?.surfaceContainerHighest)
    }

    @Test
    fun darkColorScheme_definesExpectedTokensAndContainerTiers() {
        var scheme: ColorScheme? = null
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }

        assertNotNull(scheme)
        assertEquals(Color(0xFF43474E), scheme?.outlineVariant)
        assertEquals(Color(0xFF111418), scheme?.surfaceDim)
        assertEquals(Color(0xFF37393E), scheme?.surfaceBright)
        assertEquals(md_theme_dark_primary, scheme?.surfaceTint)
        assertEquals(Color(0xFFE2E2E6), scheme?.inverseSurface)
        assertEquals(Color(0xFF2F3033), scheme?.inverseOnSurface)
        assertEquals(Color(0xFF1B5E91), scheme?.inversePrimary)
        assertEquals(Color(0xFF000000), scheme?.scrim)
        assertNotEquals(scheme?.surfaceContainerLowest, scheme?.surfaceContainerHighest)
    }

    @Test
    fun amoledColorScheme_definesExpectedTokens() {
        var scheme: ColorScheme? = null
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.AMOLED, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }

        assertNotNull(scheme)
        assertEquals(Color.Black, scheme?.background)
        assertEquals(Color.Black, scheme?.surface)
        assertEquals(Color(0xFF2E3138), scheme?.outlineVariant)
    }

    @Test
    fun oledBlackColorScheme_preservesPureBlackBackgroundAndTieredContainers() {
        var oledScheme: ColorScheme? = null
        composeTestRule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.OLED_BLACK, dynamicColor = false) {
                oledScheme = MaterialTheme.colorScheme
            }
        }

        assertEquals(Color.Black, oledScheme?.background)
        assertEquals(Color.Black, oledScheme?.surface)
        assertEquals(Color.White, oledScheme?.onBackground)
        assertEquals(Color.White, oledScheme?.onSurface)

        assertEquals(Color.Black, oledScheme?.surfaceContainerLowest)
        assertEquals(Color(0xFF0B0E11), oledScheme?.surfaceContainerLow)
        assertEquals(Color(0xFF13171C), oledScheme?.surfaceContainer)
        assertEquals(Color(0xFF1B2026), oledScheme?.surfaceContainerHigh)
        assertEquals(Color(0xFF262C34), oledScheme?.surfaceContainerHighest)
        assertEquals(Color(0xFF171B20), oledScheme?.surfaceVariant)
        assertEquals(Color.White, oledScheme?.onSurfaceVariant)
        assertEquals(Color(0xFF383E48), oledScheme?.outlineVariant)
    }

    @Test
    fun securitySemanticColors_areDefinedAndAccessible() {
        assertEquals(Color(0xFF2E7D32), SecuritySuccess)
        assertEquals(Color(0xFFD1E7DD), SecuritySuccessContainer)
        assertEquals(Color(0xFF0F5132), SecurityOnSuccessContainer)
        assertEquals(Color(0xFFED6C02), SecurityWarning)
        assertEquals(Color(0xFFFFF3CD), SecurityWarningContainer)
        assertEquals(Color(0xFF664D03), SecurityOnWarningContainer)
    }
}
