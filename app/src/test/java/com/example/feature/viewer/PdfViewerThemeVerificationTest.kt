package com.example.feature.viewer

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test for Cause A:
 * Theme.MyApplication missing Material3 attributes needed by PdfViewerFragment (pdf_viewer_fragment.xml).
 *
 * This test suite guarantees:
 * 1. Theme.MyApplication properly resolves required Material3 attributes.
 * 2. Non-Material3 themes (such as Theme.DeviceDefault.NoActionBar) fail attribute resolution,
 *    documenting why Theme.MyApplication must remain on Material3.
 * 3. Layout inflation of pdf_viewer_fragment succeeds with Theme.MyApplication.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfViewerThemeVerificationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun themeMyApplication_resolvesRequiredMaterial3Attributes() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_MyApplication)
        val theme = themedContext.theme
        val typedValue = TypedValue()

        val requiredAttributes = listOf(
            "colorSurfaceVariant" to com.google.android.material.R.attr.colorSurfaceVariant,
            "colorSurface" to com.google.android.material.R.attr.colorSurface,
            "colorOnSurface" to com.google.android.material.R.attr.colorOnSurface,
            "colorPrimary" to androidx.appcompat.R.attr.colorPrimary,
            "colorSecondary" to com.google.android.material.R.attr.colorSecondary,
            "colorOnBackground" to com.google.android.material.R.attr.colorOnBackground,
            "textAppearanceBodyMedium" to com.google.android.material.R.attr.textAppearanceBodyMedium
        )

        for ((name, attrId) in requiredAttributes) {
            val resolved = theme.resolveAttribute(attrId, typedValue, true)
            assertTrue("Theme.MyApplication must resolve Material3 attribute: $name", resolved)
        }
    }

    @Test
    fun legacyDeviceDefaultTheme_failsToResolveMaterial3Attributes_provingRegressionRisk() {
        // DeviceDefault theme was previously used, causing runtime crash during PdfViewerFragment inflation.
        val legacyContext = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val theme = legacyContext.theme
        val typedValue = TypedValue()

        val resolved = theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurfaceVariant,
            typedValue,
            true
        )
        assertFalse(
            "android:Theme.DeviceDefault.NoActionBar must NOT resolve colorSurfaceVariant, verifying regression vulnerability",
            resolved
        )
    }

    @Test
    fun pdfViewerFragmentLayout_inflatesSuccessfullyWithThemeMyApplication() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_MyApplication)
        val inflater = LayoutInflater.from(themedContext)

        val view = inflater.inflate(androidx.pdf.viewer.fragment.R.layout.pdf_viewer_fragment, null, false)
        assertNotNull("pdf_viewer_fragment layout must inflate successfully under Theme.MyApplication", view)

        // Verify subviews inflated properly
        val progressBar = view.findViewById<android.view.View>(androidx.pdf.viewer.fragment.R.id.pdfLoadingProgressBar)
        assertNotNull("pdfLoadingProgressBar must be present in inflated view", progressBar)

        val errorTextView = view.findViewById<android.widget.TextView>(androidx.pdf.viewer.fragment.R.id.errorTextView)
        assertNotNull("errorTextView must be present in inflated view", errorTextView)
    }
}
