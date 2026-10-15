package com.max97k.pddf.ui.theme

import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class OledThemeContrastTest {

    private fun getLuminance(color: Long): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0

        val rL = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gL = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bL = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }

    private fun getContrastRatio(l1: Double, l2: Double): Double {
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun testContrast(name: String, fg: Long, bg: Long) {
        val contrast = getContrastRatio(getLuminance(fg), getLuminance(bg))
        assertTrue("Contrast for $name is $contrast, expected >= 7.0 for WCAG AAA", contrast >= 7.0)
    }

    @Test
    fun testOledBlackContrastRatios() {
        val pureBlack = 0x000000L
        val pureWhite = 0xFFFFFFL

        // Background and surface to their respective on-colors
        testContrast("onBackground on background", pureWhite, pureBlack)
        testContrast("onSurface on surface", pureWhite, pureBlack)
        testContrast("onSurfaceVariant on surfaceVariant", pureWhite, pureBlack)

        // Standard theme colors inherited from dark theme against pure black background
        val primary = 0xD0BCFFL
        val secondary = 0xCCC2DCL
        val tertiary = 0xEFB8C8L
        val error = 0xF2B8B5L

        testContrast("primary on background", primary, pureBlack)
        testContrast("secondary on background", secondary, pureBlack)
        testContrast("tertiary on background", tertiary, pureBlack)
        testContrast("error on background", error, pureBlack)
    }
}
