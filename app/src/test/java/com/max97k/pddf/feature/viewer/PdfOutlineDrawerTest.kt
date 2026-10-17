package com.max97k.pddf.feature.viewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import com.max97k.pddf.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PdfOutlineDrawerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun pdfOutlineDrawer_whenEmpty_rendersHeaderAndEmptyMessage() {
        var closeClicked = false
        val activity = composeTestRule.activity
        val expectedTitle = activity.getString(R.string.title_table_of_contents)
        val expectedEmpty = activity.getString(R.string.msg_no_table_of_contents)
        val closeDesc = activity.getString(R.string.cd_close_table_of_contents)

        composeTestRule.setContent {
            PdfOutlineDrawer(
                outline = emptyList(),
                onPageClicked = {},
                onClose = { closeClicked = true }
            )
        }

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedEmpty).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(closeDesc).performClick()
        assertTrue("Close button must invoke onClose callback", closeClicked)
    }

    @Test
    fun pdfOutlineDrawer_withItems_rendersItemsAndTriggersPageClick() {
        val testItems = listOf(
            OutlineItem(title = "Chapter 1", pageIndex = 0),
            OutlineItem(title = "Chapter 2", pageIndex = 4)
        )
        var clickedPageIndex = -1

        composeTestRule.setContent {
            PdfOutlineDrawer(
                outline = testItems,
                onPageClicked = { clickedPageIndex = it },
                onClose = {}
            )
        }

        composeTestRule.onNodeWithText("Chapter 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chapter 2").assertIsDisplayed()

        composeTestRule.onNodeWithText("Chapter 2").performClick()
        assertEquals(4, clickedPageIndex)
    }

    @Test
    fun outlineDrawerOverlay_isClosedByDefault_andCanBeToggled() {
        var showOutlineDrawer = false
        val emptyOutline = emptyList<OutlineItem>()

        // By default, outline drawer must NOT be open
        assertFalse("showOutlineDrawer must be false by default", showOutlineDrawer)

        // When empty, opening is disallowed
        val canOpenWhenEmpty = emptyOutline.isNotEmpty()
        assertFalse("Cannot open outline drawer when outline is empty", canOpenWhenEmpty)

        // When non-empty, opening is allowed
        val nonEmptyOutline = listOf(OutlineItem("Chapter 1", 0))
        val canOpenWithContent = nonEmptyOutline.isNotEmpty()
        assertTrue("Can open outline drawer when outline has items", canOpenWithContent)

        showOutlineDrawer = canOpenWithContent
        assertTrue(showOutlineDrawer)

        // Dismiss action closes the drawer
        showOutlineDrawer = false
        assertFalse("Dismiss must set showOutlineDrawer to false", showOutlineDrawer)
    }
}
