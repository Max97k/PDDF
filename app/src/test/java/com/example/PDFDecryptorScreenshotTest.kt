package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.PasswordEntity
import com.example.data.ThemeMode
import com.example.feature.decrypt.PDFDecryptorScreen
import com.example.ui.theme.MyApplicationTheme
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
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class PDFDecryptorScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun screenshot_1_main_empty() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(application)

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
    val viewModel = MainViewModel(application)

    viewModel.selectedUris.value = listOf(
      Uri.parse("content://com.example.provider/Bank_Statement_July.pdf"),
      Uri.parse("content://com.example.provider/Tax_Return_2023.pdf"),
      Uri.parse("content://com.example.provider/Paystub_Oct.pdf")
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
    val viewModel = MainViewModel(application)

    val sampleUri = Uri.parse("content://com.example.provider/Decrypted_Document.pdf")
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
    val viewModel = MainViewModel(application)

    viewModel.selectedUris.value = listOf(
      Uri.parse("content://com.example.provider/Financial_Report_2024.pdf")
    )
    viewModel.selectedFileNames.value = listOf("Financial_Report_2024.pdf")
    viewModel.selectedMetadata.value = PdfMetadata(
      title = "Annual Financial Summary",
      author = "Accounting Dept",
      pageCount = 12,
      fileSizeMb = 1.82,
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
}
