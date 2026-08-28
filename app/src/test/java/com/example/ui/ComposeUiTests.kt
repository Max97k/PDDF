package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.PasswordInputSection
import com.example.SavePasswordDialog
import com.example.SelectedFilesCard

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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

        composeTestRule.onNodeWithText("Selected 2 file(s)").assertExists()
        composeTestRule.onNodeWithText("file1.pdf, file2.pdf").assertExists()
        
        composeTestRule.onNodeWithContentDescription("Clear Selection").performClick()
        assert(cleared)
    }

    @Test
    fun mainActivity_launchesWithoutCrash() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.example.MainActivity::class.java)
        scenario.onActivity { activity ->
            org.junit.Assert.assertNotNull(activity)
        }
        scenario.close()
    }
}
