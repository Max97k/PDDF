package com.example.feature.viewer

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import com.example.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Regression test suite for Cause B:
 * Compose Dialog spawning an independent Window separate from MainActivity,
 * breaking supportFragmentManager.commit.
 *
 * This test suite guarantees:
 * 1. Architectural AST contract: PdfViewerDialog must never import or use
 *    androidx.compose.ui.window.Dialog, preventing detached window creation.
 * 2. In-window overlay contract: PdfViewerDialog mounts within the host FragmentActivity's
 *    window hierarchy, allowing supportFragmentManager to commit InkPdfViewerFragment safely.
 * 3. BackHandler & dismiss flow: User back-navigation and close actions trigger onDismiss cleanly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfViewerWindowIsolationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<FragmentActivity>()

    @Before
    fun setUp() {
        PdfTestUtils.registerFakePdfService()
    }

    @Test
    fun architecturalContract_pdfViewerDialogDoesNotUseComposeDialogWindow() {
        // Find the PdfViewerDialog.kt source file
        val possiblePaths = listOf(
            File("src/main/java/com/example/feature/viewer/PdfViewerDialog.kt"),
            File("app/src/main/java/com/example/feature/viewer/PdfViewerDialog.kt")
        )
        val sourceFile = possiblePaths.firstOrNull { it.exists() }
        assertNotNull("PdfViewerDialog.kt must exist for architectural verification", sourceFile)

        val content = sourceFile!!.readText()

        // Verify that androidx.compose.ui.window.Dialog is NOT imported or invoked
        assertFalse(
            "PdfViewerDialog must NOT import androidx.compose.ui.window.Dialog to avoid window isolation",
            content.contains("import androidx.compose.ui.window.Dialog")
        )
        assertFalse(
            "PdfViewerDialog must NOT call Dialog( to prevent spawning separate Window",
            content.contains("\n    Dialog(") || content.contains("\nDialog(")
        )

        // Verify that Box and BackHandler are used for the safe in-window overlay pattern
        assertTrue(
            "PdfViewerDialog must use BackHandler for back-press interception",
            content.contains("BackHandler")
        )
        assertTrue(
            "PdfViewerDialog must use Box with zIndex overlay",
            content.contains("zIndex")
        )
    }

    @Test
    fun pdfViewerDialog_rendersWithinActivityWindowHierarchyAndAttachesFragment() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")
        var dismissed = false

        composeTestRule.setContent {
            PdfViewerDialog(
                uri = testUri,
                title = "Contract Overlay.pdf",
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify title displayed
        composeTestRule.onNodeWithText("Contract Overlay.pdf").assertIsDisplayed()

        // 2. Verify fragment was successfully attached to the Activity's FragmentManager.
        // If a separate Dialog window was used, supportFragmentManager would fail to find
        // the FragmentContainerView and throw "No view found for id".
        val fragmentManager = composeTestRule.activity.supportFragmentManager
        val fragment = fragmentManager.fragments.firstOrNull { it is InkPdfViewerFragment }
        assertNotNull("InkPdfViewerFragment must be attached inside Activity window's FragmentManager", fragment)
        assertTrue("Fragment must be in added state", fragment!!.isAdded)

        // 3. Test Close button triggers onDismiss
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertTrue("Closing top bar must trigger onDismiss", dismissed)
    }

    @Test
    fun pdfViewerDialog_backPress_triggersOnDismiss() {
        val testUri = Uri.parse("file:///android_asset/unencrypted.pdf")
        var dismissed = false

        composeTestRule.setContent {
            PdfViewerDialog(
                uri = testUri,
                title = "BackHandler Test",
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.waitForIdle()

        // Simulate hardware back press via activity onBackPressedDispatcher
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertTrue("BackHandler must trigger onDismiss upon system back press", dismissed)
    }
}
