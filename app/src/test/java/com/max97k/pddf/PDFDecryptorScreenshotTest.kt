package com.max97k.pddf

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.max97k.pddf.data.PasswordEntity
import com.max97k.pddf.data.ThemeMode
import com.max97k.pddf.feature.decrypt.AutoUnlockPasswordDialog
import com.max97k.pddf.feature.decrypt.PDFDecryptorScreen
import com.max97k.pddf.feature.vault.SavedPasswordListDialog
import com.max97k.pddf.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PDFDecryptorScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun screenshot_1_main_empty() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = createTestMainViewModel(application)

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.DARK) {
        PDFDecryptorScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_1_empty.png")
  }

  @Test
  fun screenshot_2_files_selected_batch() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = createTestMainViewModel(application)

    viewModel.selectedUris.value = listOf(
      Uri.parse("content://com.max97k.pddf.provider/Bank_Statement_July.pdf"),
      Uri.parse("content://com.max97k.pddf.provider/Tax_Return_2023.pdf"),
      Uri.parse("content://com.max97k.pddf.provider/Paystub_Oct.pdf")
    )
    viewModel.selectedFileNames.value = listOf(
      "Bank_Statement_July.pdf",
      "Tax_Return_2023.pdf",
      "Paystub_Oct.pdf"
    )
    viewModel.selectedMetadata.value = PdfMetadata(
      title = "Bank Statement July",
      author = "Financial Services Corp",
      pageCount = 6,
      fileSizeMb = 2.45,
      pdfVersion = "1.7",
      encryptionMethod = "AES 256-bit",
      canPrint = true,
      canCopy = false
    )
    viewModel.password.value = "secret123"

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.DARK) {
        PDFDecryptorScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_2_batch_selection.png")
  }

  @Test
  fun screenshot_3_decrypted_success() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = createTestMainViewModel(application)

    val sampleUri = Uri.parse("content://com.max97k.pddf.provider/Decrypted_Document.pdf")
    viewModel.selectedUris.value = listOf(sampleUri)
    viewModel.selectedFileNames.value = listOf("Encrypted_Invoice.pdf")
    viewModel.lastDecryptedUri.value = sampleUri
    viewModel.statusMessage.value = "✅ Decrypted & Saved (1 file)"

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.DARK) {
        PDFDecryptorScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_3_success_preview.png")
  }

  @Test
  fun screenshot_4_light_theme() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = createTestMainViewModel(application)

    viewModel.selectedUris.value = listOf(
      Uri.parse("content://com.max97k.pddf.provider/Financial_Report_2024.pdf")
    )
    viewModel.selectedFileNames.value = listOf("Financial_Report_2024.pdf")
    viewModel.selectedMetadata.value = PdfMetadata(
      title = "Annual Financial Summary",
      author = "Accounting Dept",
      pageCount = 12,
      fileSizeMb = 1.82,
      pdfVersion = "1.6",
      encryptionMethod = "Standard Password Protection",
      canPrint = true,
      canCopy = true
    )
    viewModel.password.value = "mypassword"

    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
        PDFDecryptorScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_4_light_theme.png")
  }

  @Test
  fun screenshot_5_autounlock_dialog() {
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
        AutoUnlockPasswordDialog(
          fileName = "encrypted.pdf",
          errorMessage = null,
          onUnlock = { _, _ -> },
          onDismiss = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_5_autounlock_dialog.png")
  }

  @Test
  fun screenshot_6_vault_dialog() {
    val samplePasswords = listOf(
      PasswordEntity(id = 1, name = "Bank Statement", passwordValue = "password123"),
      PasswordEntity(id = 2, name = "Tax Return 2024", passwordValue = "mypassword")
    )
    composeTestRule.setContent {
      MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
        SavedPasswordListDialog(
                densityMode = com.max97k.pddf.data.DensityMode.STANDARD,
          savedPasswords = samplePasswords,
          onDismiss = {},
          onSelectPassword = {},
          onDeletePassword = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_6_vault_dialog.png")
  }
}
