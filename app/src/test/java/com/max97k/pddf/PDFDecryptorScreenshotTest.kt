package com.max97k.pddf

import android.app.Application
import android.content.Context
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
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

  private fun suppressWhatsNew(application: Application) {
    val versionName = try {
      application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: "0.1.0"
    } catch (_: Exception) {
      "0.1.0"
    }
    application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
      .edit()
      .putString("last_seen_version", versionName)
      .commit()
  }

  @Suppress("UNCHECKED_CAST")
  private fun updateUiState(viewModel: MainViewModel, transform: (MainUiState) -> MainUiState) {
    val field = MainViewModel::class.java.getDeclaredField("_uiState")
    field.isAccessible = true
    val flow = field.get(viewModel) as MutableStateFlow<MainUiState>
    flow.value = transform(flow.value)
  }

  @Test
  fun screenshot_1_main_empty() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    suppressWhatsNew(application)
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
    suppressWhatsNew(application)
    val viewModel = createTestMainViewModel(application)

    val uris = persistentListOf(
      Uri.parse("content://com.max97k.pddf.provider/Bank_Statement_July.pdf"),
      Uri.parse("content://com.max97k.pddf.provider/Tax_Return_2023.pdf"),
      Uri.parse("content://com.max97k.pddf.provider/Paystub_Oct.pdf")
    )
    val names = persistentListOf(
      "Bank_Statement_July.pdf",
      "Tax_Return_2023.pdf",
      "Paystub_Oct.pdf"
    )
    val meta = PdfMetadata(
      title = "Bank Statement July",
      author = "Financial Services Corp",
      pageCount = 6,
      fileSizeMb = 2.45,
      pdfVersion = "1.7",
      encryptionMethod = "AES 256-bit",
      canPrint = true,
      canCopy = false
    )
    viewModel.selectedUris.value = uris
    viewModel.selectedFileNames.value = names
    viewModel.selectedMetadata.value = meta
    viewModel.password.value = "secret123"
    updateUiState(viewModel) {
      it.copy(
        selectedUris = uris,
        selectedFileNames = names,
        selectedMetadata = meta,
        password = "secret123"
      )
    }

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
    suppressWhatsNew(application)
    val viewModel = createTestMainViewModel(application)

    val sampleUri = Uri.parse("content://com.max97k.pddf.provider/Decrypted_Document.pdf")
    val uris = persistentListOf(sampleUri)
    val names = persistentListOf("Encrypted_Invoice.pdf")
    viewModel.selectedUris.value = uris
    viewModel.selectedFileNames.value = names
    viewModel.lastDecryptedUri.value = sampleUri
    viewModel.statusMessage.value = "✅ Decrypted & Saved (1 file)"
    updateUiState(viewModel) {
      it.copy(
        selectedUris = uris,
        selectedFileNames = names,
        lastDecryptedUri = sampleUri,
        statusMessage = "✅ Decrypted & Saved (1 file)",
        lastDecryptionDurationMs = 42L
      )
    }

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
    suppressWhatsNew(application)
    val viewModel = createTestMainViewModel(application)

    val uris = persistentListOf(
      Uri.parse("content://com.max97k.pddf.provider/Financial_Report_2024.pdf")
    )
    val names = persistentListOf("Financial_Report_2024.pdf")
    val meta = PdfMetadata(
      title = "Annual Financial Summary",
      author = "Accounting Dept",
      pageCount = 12,
      fileSizeMb = 1.82,
      pdfVersion = "1.6",
      encryptionMethod = "Standard Password Protection",
      canPrint = true,
      canCopy = true
    )
    viewModel.selectedUris.value = uris
    viewModel.selectedFileNames.value = names
    viewModel.selectedMetadata.value = meta
    viewModel.password.value = "mypassword"
    updateUiState(viewModel) {
      it.copy(
        selectedUris = uris,
        selectedFileNames = names,
        selectedMetadata = meta,
        password = "mypassword",
        themeMode = ThemeMode.LIGHT
      )
    }

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
          onDeletePassword = {}, onExportClick = {}, onImportClick = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_6_vault_dialog.png")
  }
}
