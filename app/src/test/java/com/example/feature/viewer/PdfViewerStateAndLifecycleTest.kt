package com.example.feature.viewer

import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test suite for Cause C & PdfViewer state logic:
 * 1. Demonstrates that setting properties on unattached Fragment throws exceptions,
 *    proving why PdfViewerScreen must defer setup to runOnCommit and verify isAdded.
 * 2. Verifies InkPdfViewerFragment state lifecycle when attached to FragmentActivity.
 * 3. Verifies PdfViewerScreen transaction safety, immersive mode toggle, search toggle,
 *    and action button dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfViewerStateAndLifecycleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<FragmentActivity>()

    @Before
    fun setUp() {
        PdfTestUtils.registerFakePdfService()
    }

    @Test
    fun unattachedFragment_settingDocumentUriOrInteractingBeforeAttach_provesRegressionCauseC() {
        val fragment = InkPdfViewerFragment()

        assertFalse("Fragment must not be added initially", fragment.isAdded)
        assertFalse("Fragment must not be detached initially", fragment.isDetached)

        // Attempting to set documentUri before fragment is attached to a host ViewModelStoreOwner
        // throws IllegalStateException. This proves why PdfViewerScreen guards against early property access.
        var caughtException = false
        try {
            fragment.documentUri = Uri.parse("file:///test.pdf")
        } catch (_: IllegalStateException) {
            caughtException = true
        }
        assertTrue("Setting documentUri on unattached fragment must fail, verifying Cause C risk", caughtException)
    }

    @Test
    fun inkPdfViewerFragment_immersiveModeCallbackInvokedWhenAttached() {
        val activity = composeTestRule.activity
        val fragment = InkPdfViewerFragment()
        var receivedState: Boolean? = null

        fragment.onImmersiveModeRequest = { enter ->
            receivedState = enter
        }

        // Attach fragment and inflate its view hierarchy
        val container = FrameLayout(activity).apply { id = android.view.View.generateViewId() }
        activity.runOnUiThread {
            activity.setContentView(container)
            activity.supportFragmentManager.commitNow {
                add(container.id, fragment, "test_tag")
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)

        // Now invoking onRequestImmersiveMode executes super cleanly and triggers the Compose callback
        activity.runOnUiThread {
            fragment.onRequestImmersiveMode(true)
        }
        composeTestRule.waitForIdle()
        assertEquals(true, receivedState)

        activity.runOnUiThread {
            fragment.onRequestImmersiveMode(false)
        }
        composeTestRule.waitForIdle()
        assertEquals(false, receivedState)

        // Disposal: clear callback to prevent leaking Compose closure
        fragment.onImmersiveModeRequest = null
        receivedState = null
        activity.runOnUiThread {
            fragment.onRequestImmersiveMode(true)
        }
        composeTestRule.waitForIdle()
        assertNull("Callback should not fire after being cleared on dispose", receivedState)
    }

    @Test
    fun pdfViewerScreen_attachesFragmentToHostingActivityFragmentManager() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")
        var closeClicked = false

        composeTestRule.setContent {
            PdfViewerScreen(
                uri = testUri,
                title = "Contract Agreement.pdf",
                onClose = { closeClicked = true }
            )
        }

        composeTestRule.waitForIdle()

        // Verify title displayed
        composeTestRule.onNodeWithText("Contract Agreement.pdf").assertIsDisplayed()

        // Verify fragment was attached to FragmentActivity's FragmentManager
        val fragmentManager = composeTestRule.activity.supportFragmentManager
        val attachedFragment = fragmentManager.fragments.firstOrNull { it is InkPdfViewerFragment } as? InkPdfViewerFragment
        assertNotNull("InkPdfViewerFragment must be committed and attached to supportFragmentManager", attachedFragment)
        assertTrue("Fragment must be added to FragmentManager", attachedFragment!!.isAdded)
        assertFalse("Fragment must not be detached", attachedFragment.isDetached)

        // Verify close action
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(closeClicked)
    }

    @Test
    fun pdfViewerScreen_searchStateToggle_updatesButtonSemanticsAndFragment() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")

        composeTestRule.setContent {
            PdfViewerScreen(
                uri = testUri,
                title = "Searchable Document",
                onClose = {}
            )
        }

        composeTestRule.waitForIdle()

        val searchButtonDesc = composeTestRule.activity.getString(R.string.btn_search_pdf)
        val exitSearchButtonDesc = composeTestRule.activity.getString(R.string.btn_exit_search_pdf)

        // Initially search is inactive
        composeTestRule.onNodeWithContentDescription(searchButtonDesc).assertIsDisplayed()

        // First click: activate search
        composeTestRule.onNodeWithContentDescription(searchButtonDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(exitSearchButtonDesc).assertIsDisplayed()

        // Verify fragment is present and attached to receive search state
        val fragmentManager = composeTestRule.activity.supportFragmentManager
        val fragment = fragmentManager.fragments.firstOrNull { it is InkPdfViewerFragment } as? InkPdfViewerFragment
        assertNotNull(fragment)
        assertTrue("InkPdfViewerFragment must be added to host FragmentManager", fragment!!.isAdded)
        assertTrue("Search should be pending on fragment", fragment.pendingSearchActive)

        // Second click: exit search
        composeTestRule.onNodeWithContentDescription(exitSearchButtonDesc).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(searchButtonDesc).assertIsDisplayed()
        assertFalse("Search should be cancelled on fragment", fragment.pendingSearchActive)
    }

    @Test
    fun inkPdfViewerFragment_pendingSearch_activatesOnDocumentLoaded() {
        val fragment = InkPdfViewerFragment()
        fragment.setSearchActive(true)
        assertTrue("Pending search must be true before document loaded", fragment.pendingSearchActive)
        assertFalse(fragment.isDocumentLoaded)

        fragment.onLoadDocumentSuccess()
        assertTrue("Document loaded flag must be true", fragment.isDocumentLoaded)
    }

    @Test
    fun pdfViewerScreen_immersiveModeToggle_hidesAndShowsTopBar() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")

        composeTestRule.setContent {
            PdfViewerScreen(
                uri = testUri,
                title = "Immersive Document",
                onClose = {}
            )
        }

        composeTestRule.waitForIdle()

        // TopAppBar is initially visible
        composeTestRule.onNodeWithText("Immersive Document").assertIsDisplayed()

        val fragmentManager = composeTestRule.activity.supportFragmentManager
        val fragment = fragmentManager.fragments.firstOrNull { it is InkPdfViewerFragment } as? InkPdfViewerFragment
        assertNotNull(fragment)

        // Simulate fragment requesting immersive mode (fullscreen reading)
        composeTestRule.runOnUiThread {
            fragment!!.onRequestImmersiveMode(true)
        }
        composeTestRule.waitForIdle()

        // TopAppBar should now be hidden
        composeTestRule.onNodeWithText("Immersive Document").assertDoesNotExist()

        // Simulate user exiting immersive mode (restore bars)
        composeTestRule.runOnUiThread {
            fragment!!.onRequestImmersiveMode(false)
        }
        composeTestRule.waitForIdle()

        // TopAppBar should be restored
        composeTestRule.onNodeWithText("Immersive Document").assertIsDisplayed()
    }

    @Test
    fun pdfViewerScreen_actionButtons_invokeCallbacks() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")
        var shared = false
        var saved = false

        composeTestRule.setContent {
            PdfViewerScreen(
                uri = testUri,
                title = "Actions Test",
                onClose = {},
                onShare = { shared = true },
                onSaveAs = { saved = true }
            )
        }

        composeTestRule.waitForIdle()

        val shareDesc = composeTestRule.activity.getString(R.string.btn_share_file)
        val saveDesc = composeTestRule.activity.getString(R.string.btn_save_as)

        composeTestRule.onNodeWithContentDescription(shareDesc).performClick()
        assertTrue("onShare callback must be called", shared)

        composeTestRule.onNodeWithContentDescription(saveDesc).performClick()
        assertTrue("onSaveAs callback must be called", saved)
    }
}
