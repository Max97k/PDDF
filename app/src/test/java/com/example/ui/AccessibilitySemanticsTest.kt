package com.example.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.PasswordEntity
import com.example.feature.decrypt.AutoUnlockPasswordDialog
import com.example.feature.decrypt.BatchProgressDialog
import com.example.feature.vault.SavedPasswordListDialog
import com.example.ui.components.SelectedFilesCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AccessibilitySemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun autoUnlockPasswordDialog_hasRoleCheckboxSemantics() {
        composeTestRule.setContent {
            AutoUnlockPasswordDialog(
                fileName = "sample.pdf",
                errorMessage = null,
                onUnlock = { _, _ -> },
                onDismiss = {}
            )
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertExists()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test
    fun batchProgressDialog_hasLiveRegionPoliteSemantics() {
        composeTestRule.setContent {
            BatchProgressDialog(
                progress = 3,
                total = 10,
                onCancel = {}
            )
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
            .assertTextContains("3 of 10 completed")
    }

    @Test
    fun selectedFilesCard_hasLiveRegionPoliteSemantics() {
        composeTestRule.setContent {
            SelectedFilesCard(
                fileNames = listOf("doc1.pdf", "doc2.pdf"),
                fileCount = 2,
                onClear = {}
            )
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
    }

    @Test
    fun savedPasswordListDialog_emptyState_hasLiveRegionPolite() {
        composeTestRule.setContent {
            SavedPasswordListDialog(
                savedPasswords = listOf(
                    PasswordEntity(id = 1, name = "Bank", passwordValue = "pass123")
                ),
                onDismiss = {},
                onSelectPassword = {},
                onDeletePassword = {}
            )
        }

        composeTestRule.onNodeWithText("Search").performTextInput("XYZNonExistent")

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
    }

    @Test
    fun savedPasswordListDialog_itemsHaveAtLeast48dpHeight() {
        val passwords = listOf(
            PasswordEntity(id = 1, name = "Bank Statement", passwordValue = "pass123")
        )

        composeTestRule.setContent {
            SavedPasswordListDialog(
                savedPasswords = passwords,
                onDismiss = {},
                onSelectPassword = {},
                onDeletePassword = {}
            )
        }

        composeTestRule
            .onNodeWithText("Bank Statement", substring = true)
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun autoUnlockPasswordDialog_checkboxRowHasAtLeast48dpHeight() {
        composeTestRule.setContent {
            AutoUnlockPasswordDialog(
                fileName = "sample.pdf",
                errorMessage = null,
                onUnlock = { _, _ -> },
                onDismiss = {}
            )
        }

        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
    }
}
