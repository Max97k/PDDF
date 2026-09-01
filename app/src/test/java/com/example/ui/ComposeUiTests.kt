package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.ui.components.PasswordInputSection
import com.example.feature.vault.SavePasswordDialog
import com.example.ui.components.SelectedFilesCard
import com.example.ui.components.DocumentDetailsCard
import com.example.ui.components.ThemeDropdownMenu
import com.example.feature.decrypt.BatchProgressDialog
import com.example.feature.decrypt.AutoUnlockPasswordDialog
import com.example.feature.vault.SavedPasswordListDialog
import com.example.feature.viewer.PdfViewerScreen
import com.example.PdfMetadata
import com.example.data.PasswordEntity
import com.example.data.ThemeMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComposeUiTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun passwordInputSection_displaysCorrectly() {
        var passwordChangedTo = ""
        var toggleClicked = false
        var listClicked = false
        var saveClicked = false

        composeTestRule.setContent {
            PasswordInputSection(
                password = "test",
                passwordVisible = true,
                onPasswordChange = { passwordChangedTo = it },
                onTogglePasswordVisible = { toggleClicked = true },
                onOpenPasswordList = { listClicked = true },
                onOpenSavePassword = { saveClicked = true }
            )
        }

        composeTestRule.onNodeWithText("PDF Password").assertExists()
        composeTestRule.onNodeWithText("test").assertExists()
        
        composeTestRule.onNodeWithContentDescription("Hide password").performClick()
        assert(toggleClicked)
        
        composeTestRule.onNodeWithContentDescription("Saved Passwords").performClick()
        assert(listClicked)
        
        composeTestRule.onNodeWithContentDescription("Save Password").performClick()
        assert(saveClicked)
        
        composeTestRule.onNodeWithText("test").performTextReplacement("test123")
        assert(passwordChangedTo == "test123")
    }

    @Test
    fun savePasswordDialog_displaysAndSavesCorrectly() {
        var savedName = ""
        var savedPass = ""
        var dismissed = false

        composeTestRule.setContent {
            SavePasswordDialog(
                currentPassword = "mypassword",
                onDismiss = { dismissed = true },
                onSave = { name, pass -> 
                    savedName = name
                    savedPass = pass
                }
            )
        }

        composeTestRule.onNodeWithText("Save Password").assertExists()
        composeTestRule.onNodeWithText("Save").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()

        // Empty name shouldn't save
        composeTestRule.onNodeWithText("Save").performClick()
        assert(savedName == "")
        
        // Type a name and save
        composeTestRule.onNodeWithText("Password Name (e.g., Bank Statement)").performTextInput("MyBank")
        composeTestRule.onNodeWithText("Save").performClick()
        
        assert(savedName == "MyBank")
        assert(savedPass == "mypassword")
        
        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(dismissed)
    }

    @Test
    fun selectedFilesCard_displaysAndClearsCorrectly() {
        var cleared = false

        composeTestRule.setContent {
            SelectedFilesCard(
                fileNames = listOf("file1.pdf", "file2.pdf"),
                fileCount = 2,
                onClear = { cleared = true }
            )
        }

        composeTestRule.onNodeWithText("2 files selected").assertExists()
        composeTestRule.onNodeWithText("file1.pdf, file2.pdf").assertExists()
        
        composeTestRule.onNodeWithContentDescription("Clear Selection").performClick()
        assert(cleared)
    }

    @Test
    fun documentDetailsCard_expandsAndShowsMetadata() {
        val metadata = PdfMetadata(
            title = "Test Statement",
            author = "Financial Corp",
            pageCount = 12,
            fileSizeMb = 1.25,
            encryptionMethod = "AES-128",
            canPrint = true,
            canCopy = false
        )

        composeTestRule.setContent {
            DocumentDetailsCard(metadata = metadata)
        }

        composeTestRule.onNodeWithText("Document Details").assertExists()
        composeTestRule.onNodeWithContentDescription("Expand").performClick()

        composeTestRule.onNodeWithText("Title: Test Statement").assertExists()
        composeTestRule.onNodeWithText("Author: Financial Corp").assertExists()
        composeTestRule.onNodeWithText("Pages: 12").assertExists()
        composeTestRule.onNodeWithText("File Size: 1.25 MB").assertExists()
        composeTestRule.onNodeWithText("Encryption: AES-128").assertExists()
        composeTestRule.onNodeWithText("Permissions: Printing allowed, Copying denied").assertExists()
    }

    @Test
    fun batchProgressDialog_displaysAndCancels() {
        var cancelled = false
        composeTestRule.setContent {
            BatchProgressDialog(
                progress = 3,
                total = 10,
                onCancel = { cancelled = true }
            )
        }

        composeTestRule.onNodeWithText("Processing Batch").assertExists()
        composeTestRule.onNodeWithText("3 of 10 completed").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(cancelled)
    }

    @Test
    fun autoUnlockPasswordDialog_displaysAndUnlocks() {
        var unlockedPass = ""
        var remember = false
        var dismissed = false

        composeTestRule.setContent {
            AutoUnlockPasswordDialog(
                fileName = "sample_bank.pdf",
                errorMessage = null,
                onUnlock = { pass, rem ->
                    unlockedPass = pass
                    remember = rem
                },
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithText("Unlock PDF").assertExists()
        composeTestRule.onNodeWithText("sample_bank.pdf").assertExists()
        composeTestRule.onNodeWithText("Remember this password").assertExists()

        composeTestRule.onNodeWithText("PDF Password").performTextInput("SecretPass1")
        composeTestRule.onNodeWithText("Unlock & View").performClick()

        assert(unlockedPass == "SecretPass1")
        assert(remember)

        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(dismissed)
    }

    @Test
    fun savedPasswordListDialog_searchAndFilter() {
        val passwords = listOf(
            PasswordEntity(id = 1, name = "Bank Statement", passwordValue = "bank123"),
            PasswordEntity(id = 2, name = "Tax Return", passwordValue = "tax456")
        )
        var selected = ""
        var deleted: PasswordEntity? = null
        var dismissed = false

        composeTestRule.setContent {
            SavedPasswordListDialog(
                savedPasswords = passwords,
                onDismiss = { dismissed = true },
                onSelectPassword = { selected = it },
                onDeletePassword = { deleted = it }
            )
        }

        composeTestRule.onNodeWithText("Saved Passwords").assertExists()
        composeTestRule.onNodeWithText("Bank Statement").assertExists()
        composeTestRule.onNodeWithText("Tax Return").assertExists()

        // Filter search
        composeTestRule.onNodeWithText("Search").performTextInput("Bank")
        composeTestRule.onNodeWithText("Bank Statement").assertExists()
        composeTestRule.onNodeWithText("Tax Return").assertDoesNotExist()

        // Clear search
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        composeTestRule.onNodeWithText("Tax Return").assertExists()

        // Delete item
        composeTestRule.onNodeWithContentDescription("Delete Tax Return").performClick()
        assert(deleted?.name == "Tax Return")

        // Select item
        composeTestRule.onNodeWithText("Bank Statement").performClick()
        assert(selected == "bank123")

        // Close
        composeTestRule.onNodeWithText("Close").performClick()
        assert(dismissed)
    }

    @Test
    fun themeDropdownMenu_selectionWorks() {
        var selectedTheme: ThemeMode? = null
        composeTestRule.setContent {
            ThemeDropdownMenu(onThemeSelected = { selectedTheme = it })
        }

        composeTestRule.onNodeWithContentDescription("Theme Settings").performClick()
        composeTestRule.onNodeWithText("AMOLED Black").performClick()
        assert(selectedTheme == ThemeMode.AMOLED)
    }

    @Test
    fun mainActivity_launchesWithoutCrash() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.example.MainActivity::class.java)
        scenario.onActivity { activity ->
            org.junit.Assert.assertNotNull(activity)
        }
        scenario.close()
    }

    @Test
    fun pdfViewerScreen_handlesInvalidUriGracefully() {
        var closed = false
        composeTestRule.setContent {
            PdfViewerScreen(
                uri = android.net.Uri.parse("file:///nonexistent.pdf"),
                title = "Test PDF",
                onClose = { closed = true }
            )
        }

        composeTestRule.onNodeWithText("Test PDF").assertExists()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assert(closed)
    }
}
