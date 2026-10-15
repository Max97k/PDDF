package com.max97k.pddf.feature.viewer

import android.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfViewerSmartNightModeColorMatrixTest {

    @Test
    fun testPhotoPreservingSmartNightModeMatrix_isHueRotatedNegative() {
        val matrix = ColorMatrix(floatArrayOf(
            -0.574f, 1.43f, 0.144f, 0f, 0f,
             0.426f, 0.43f, 0.144f, 0f, 0f,
             0.426f, 1.43f, -0.856f, 0f, 0f,
             0f, 0f, 0f, 1f, 0f
        ))

        val array = matrix.array
        assertEquals(-0.574f, array[0])
        assertEquals(0.426f, array[5])
        assertEquals(-0.856f, array[12])
    }
}
