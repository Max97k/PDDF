# Handoff Report — Milestone 1 Explorer 1: MainActivity & Component Extraction

## 1. Observation
- **Monolithic `MainActivity.kt`**: Located at `app/src/main/java/com/example/MainActivity.kt`, total 1,245 lines. It currently contains the activity lifecycle, intent handling, root UI scaffold, screen composable `PDFDecryptorScreen`, dialogs (`SavePasswordDialog`, `SavedPasswordListDialog`, `BatchProgressDialog`, `AutoUnlockPasswordDialog`, `WhatsNewDialog`), UI cards (`SelectedFilesCard`, `PasswordInputSection`, `DocumentDetailsCard`, `EmptyStateCard`), theme dropdown (`ThemeDropdownMenu`), and biometric authentication callback logic.
- **`PdfViewer.kt`**: Located at `app/src/main/java/com/example/ui/PdfViewer.kt`, total 403 lines. Contains `PdfViewerDialog`, `PdfViewerScreen`, and `PdfPageItem`. Currently lacks separation into `feature/viewer/` sub-package.
- **Violation of Leaf Composable Decoupling**: While `SelectedFilesCard`, `PasswordInputSection`, `SavePasswordDialog`, and `SavedPasswordListDialog` already take state and callbacks, several subcomponents (like `ThemeDropdownMenu`, `EmptyStateCard`, `WhatsNewDialog`, `BatchProgressDialog`, `AutoUnlockPasswordDialog`) are declared inline inside `PDFDecryptorScreen`, directly manipulating `viewModel.*.value` flows.
- **Existing Test Dependencies**:
  - `ComposeUiTests.kt` tests `PasswordInputSection`, `SavePasswordDialog`, `SelectedFilesCard`, and `PdfViewerScreen`.
  - `MainActivityTest.kt` tests `getFileName(context, uri)` and `MainActivity` intent routing.
  - `PDFDecryptorScreenshotTest.kt` and `MultiDeviceScreenshotTest.kt` instantiate `PDFDecryptorScreen(viewModel = viewModel, ...)`.

---

## 2. Logic Chain
1. **Separation of Concerns & Modularity**:
   - `MainActivity.kt` should focus exclusively on Android Activity lifecycle (`onCreate`, `onResume`, `onPause`, `onNewIntent`), system edge-to-edge insets, window size calculation, and intent routing. This reduces `MainActivity.kt` from 1,245 lines to ~90 lines.
   - UI components that are reusable across screens should be located in `com.example.ui.components`.
   - Feature-specific screens, dialogs, and helper modules should be grouped under `com.example.feature.<feature_name>` (`decrypt`, `vault`, `viewer`).
2. **Leaf Composable Decoupling & Stability**:
   - Leaf composables (`SelectedFilesCard`, `PasswordInputSection`, `DocumentDetailsCard`, `EmptyStateCard`, `ThemeDropdownMenu`, `WhatsNewDialog`, `SavePasswordDialog`, `SavedPasswordListDialog`, `BatchProgressDialog`, `AutoUnlockPasswordDialog`, `PdfPageItem`) must receive only primitive or immutable state and lambda callbacks.
   - In accordance with AGENTS.md, all composable functions emitting UI must have `modifier: Modifier = Modifier` as the first optional parameter.
   - Screen-level composables (`PDFDecryptorScreen`, `PdfViewerScreen`) manage state collection and pass down clean props.
3. **Dispatchers.IO Isolation & Resource Safety**:
   - `PdfPageItem` and `PdfViewerScreen` handle file descriptors and PDF rendering strictly with `withContext(Dispatchers.IO)` and use Kotlin `.use { ... }` blocks for streams and `FileUtils.secureDelete(tempFile)` upon disposal.
4. **Biometric Decoupling**:
   - Biometric authentication logic in `PDFDecryptorScreen` is extracted into `com.example.feature.vault.BiometricHelper`, providing a clean, reusable API for hardware-backed authentication (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`).

---

## 3. Caveats
- **Parallel Explorer Scopes**:
  - `m1_explorer_2` is designing the unified `MainUiState` data class, `UiEffect` event channel, and `MainViewModel` refactoring.
  - `m1_explorer_3` is analyzing unit and screenshot test updates and verification.
- **Compatibility Bridge**:
  - `PDFDecryptorScreen` will support both a ViewModel-backed root signature `PDFDecryptorScreen(viewModel, modifier, snackbarHostState, windowWidthSizeClass)` (for Activity and screenshot tests) and pure composable delegation to all decomposed leaf components.
  - Top-level `fun getFileName(context: Context, uri: Uri): String = FileUtils.getFileName(context, uri)` should be preserved or aliased in `MainActivity.kt` / `FileUtils.kt` to avoid breaking existing unit tests in `MainActivityTest.kt`.

---

## 4. Conclusion & Concrete Specification

### 4.1 Target File Layout
```
app/src/main/java/com/example/
├── MainActivity.kt                                # Slim Activity (~90 lines)
├── feature/
│   ├── decrypt/
│   │   ├── PDFDecryptorScreen.kt                  # Primary screen composable
│   │   ├── BatchProgressDialog.kt                 # Batch progress dialog
│   │   └── AutoUnlockPasswordDialog.kt            # Auto-unlock password prompt dialog
│   ├── vault/
│   │   ├── SavedPasswordListDialog.kt             # Searchable password vault dialog
│   │   ├── SavePasswordDialog.kt                  # Save password dialog
│   │   └── BiometricHelper.kt                     # BiometricPrompt manager
│   └── viewer/
│       ├── PdfViewerDialog.kt                     # Fullscreen dialog container
│       ├── PdfViewerScreen.kt                     # PDF viewer with TopBar, gestures, and list
│       └── PdfPageItem.kt                         # Async page rendering with LRU cache
└── ui/
    └── components/
        ├── SelectedFilesCard.kt                   # Selected file count & list card
        ├── PasswordInputSection.kt                # Password text field + action buttons
        ├── DocumentDetailsCard.kt                 # Expandable PDF metadata inspector
        ├── EmptyStateCard.kt                      # Zero-state illustration & guide
        ├── ThemeDropdownMenu.kt                   # Theme selector dropdown
        └── WhatsNewDialog.kt                      # Changelog dialog
```

---

### 4.2 Exact File Specifications & Implementations

#### File 1: `app/src/main/java/com/example/MainActivity.kt`
- **Package**: `package com.example`
- **Imports**:
  ```kotlin
  import android.content.Context
  import android.content.Intent
  import android.net.Uri
  import android.os.Build
  import android.os.Bundle
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import androidx.activity.viewModels
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.SnackbarHost
  import androidx.compose.material3.SnackbarHostState
  import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
  import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.remember
  import androidx.compose.ui.Modifier
  import androidx.fragment.app.FragmentActivity
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.example.feature.decrypt.PDFDecryptorScreen
  import com.example.ui.theme.MyApplicationTheme
  import com.example.util.FileUtils
  ```
- **Implementation**:
  ```kotlin
  package com.example

  import android.content.Context
  import android.content.Intent
  import android.net.Uri
  import android.os.Build
  import android.os.Bundle
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import androidx.activity.viewModels
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.SnackbarHost
  import androidx.compose.material3.SnackbarHostState
  import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
  import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.remember
  import androidx.compose.ui.Modifier
  import androidx.fragment.app.FragmentActivity
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.example.feature.decrypt.PDFDecryptorScreen
  import com.example.ui.theme.MyApplicationTheme
  import com.example.util.FileUtils

  class MainActivity : FragmentActivity() {

      private val viewModel: MainViewModel by viewModels()

      @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          enableEdgeToEdge()
          handleIntent(intent)
          setContent {
              val windowSizeClass = calculateWindowSizeClass(this)
              val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
              MyApplicationTheme(themeMode = themeMode) {
                  val snackbarHostState = remember { SnackbarHostState() }
                  Scaffold(
                      modifier = Modifier.fillMaxSize(),
                      snackbarHost = { SnackbarHost(snackbarHostState) }
                  ) { innerPadding ->
                      PDFDecryptorScreen(
                          viewModel = viewModel,
                          modifier = Modifier.padding(innerPadding),
                          snackbarHostState = snackbarHostState,
                          windowWidthSizeClass = windowSizeClass.widthSizeClass
                      )
                  }
              }
          }
      }

      override fun onPause() {
          super.onPause()
          viewModel.onAppBackgrounded()
      }

      override fun onResume() {
          super.onResume()
          viewModel.onAppForegrounded()
      }

      override fun onNewIntent(intent: Intent) {
          super.onNewIntent(intent)
          setIntent(intent)
          handleIntent(intent)
      }

      private fun handleIntent(intent: Intent?) {
          if (intent == null) return
          val action = intent.action

          if (action == "com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS" || action == "com.example.ACTION_SHOW_SAVED_PASSWORDS") {
              viewModel.showPasswordListDialog.value = true
              return
          } else if (action == "com.max97k.pddf.ACTION_SELECT_PDF" || action == "com.example.ACTION_SELECT_PDF") {
              viewModel.triggerOpenDocumentPicker()
              return
          }

          val uris = mutableListOf<Uri>()

          if (Intent.ACTION_VIEW == action) {
              intent.data?.let { uris.add(it) }
          } else if (Intent.ACTION_SEND == action) {
              val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
              } else {
                  @Suppress("DEPRECATION")
                  intent.getParcelableExtra(Intent.EXTRA_STREAM)
              }
              uri?.let { uris.add(it) }
          } else if (Intent.ACTION_SEND_MULTIPLE == action) {
              val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
              } else {
                  @Suppress("DEPRECATION")
                  intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
              }
              streamUris?.let { uris.addAll(it) }
          }

          if (uris.isNotEmpty()) {
              viewModel.setSelectedUris(this, uris)
              if (uris.size == 1 && (Intent.ACTION_VIEW == action || Intent.ACTION_SEND == action)) {
                  viewModel.startAutoUnlockFlow(this, uris.first()) {}
              }
          }
      }
  }

  fun getFileName(context: Context, uri: Uri): String {
      return FileUtils.getFileName(context, uri)
  }
  ```

---

#### File 2: `app/src/main/java/com/example/ui/components/SelectedFilesCard.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun SelectedFilesCard(
      fileNames: List<String>,
      fileCount: Int,
      onClear: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Close
  import androidx.compose.material3.Card
  import androidx.compose.material3.CardDefaults
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.example.R

  @Composable
  fun SelectedFilesCard(
      fileNames: List<String>,
      fileCount: Int,
      onClear: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      val haptic = LocalHapticFeedback.current
      Card(
          modifier = modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
          Column(modifier = Modifier.padding(16.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(
                      text = stringResource(R.string.label_selected_files_count, fileCount),
                      style = MaterialTheme.typography.titleMedium
                  )
                  IconButton(onClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                      onClear()
                  }) {
                      Icon(
                          Icons.Default.Close,
                          contentDescription = stringResource(R.string.content_desc_clear_selection)
                      )
                  }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text = if (fileNames.isNotEmpty()) fileNames.joinToString(", ") else stringResource(R.string.btn_select_pdfs),
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 3,
                  overflow = TextOverflow.Ellipsis
              )
          }
      }
  }
  ```

---

#### File 3: `app/src/main/java/com/example/ui/components/PasswordInputSection.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun PasswordInputSection(
      password: String,
      passwordVisible: Boolean,
      onPasswordChange: (String) -> Unit,
      onTogglePasswordVisible: () -> Unit,
      onOpenPasswordList: () -> Unit,
      onOpenSavePassword: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.text.KeyboardOptions
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.List
  import androidx.compose.material.icons.filled.Save
  import androidx.compose.material.icons.filled.Visibility
  import androidx.compose.material.icons.filled.VisibilityOff
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.OutlinedTextField
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.input.ImeAction
  import androidx.compose.ui.text.input.KeyboardType
  import androidx.compose.ui.text.input.PasswordVisualTransformation
  import androidx.compose.ui.text.input.VisualTransformation
  import androidx.compose.ui.unit.dp
  import com.example.R

  @Composable
  fun PasswordInputSection(
      password: String,
      passwordVisible: Boolean,
      onPasswordChange: (String) -> Unit,
      onTogglePasswordVisible: () -> Unit,
      onOpenPasswordList: () -> Unit,
      onOpenSavePassword: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      val haptic = LocalHapticFeedback.current
      Row(
          modifier = modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
      ) {
          OutlinedTextField(
              value = password,
              onValueChange = onPasswordChange,
              label = { Text(stringResource(R.string.label_password)) },
              singleLine = true,
              modifier = Modifier.weight(1f),
              keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Password,
                  imeAction = ImeAction.Done
              ),
              visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                  val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                  val description = if (passwordVisible) "Hide password" else "Show password"
                  IconButton(onClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      onTogglePasswordVisible()
                  }) {
                      Icon(
                          imageVector = image,
                          contentDescription = description
                      )
                  }
              }
          )
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onOpenPasswordList()
          }) {
              Icon(
                  Icons.Default.List,
                  contentDescription = stringResource(R.string.content_desc_saved_passwords)
              )
          }
          IconButton(
              onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onOpenSavePassword()
              },
              enabled = password.isNotBlank()
          ) {
              Icon(
                  Icons.Default.Save,
                  contentDescription = stringResource(R.string.content_desc_save_password)
              )
          }
      }
  }
  ```

---

#### File 4: `app/src/main/java/com/example/ui/components/DocumentDetailsCard.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun DocumentDetailsCard(
      metadata: PdfMetadata,
      modifier: Modifier = Modifier,
      onPreview: (() -> Unit)? = null
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Visibility
  import androidx.compose.material.icons.filled.VisibilityOff
  import androidx.compose.material3.Card
  import androidx.compose.material3.CardDefaults
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import com.example.PdfMetadata
  import com.example.R
  import java.util.Locale

  @Composable
  fun DocumentDetailsCard(
      metadata: PdfMetadata,
      modifier: Modifier = Modifier,
      onPreview: (() -> Unit)? = null
  ) {
      var expanded by remember { mutableStateOf(false) }
      Card(
          modifier = modifier
              .fillMaxWidth()
              .clickable { expanded = !expanded },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
          Column(modifier = Modifier.padding(16.dp)) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text("Document Details", style = MaterialTheme.typography.titleMedium)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                      if (onPreview != null) {
                          TextButton(onClick = onPreview) {
                              Text("👁️ " + stringResource(R.string.btn_preview_pdf))
                          }
                      }
                      Icon(
                          imageVector = if (expanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                          contentDescription = if (expanded) "Collapse" else "Expand"
                      )
                  }
              }
              if (expanded) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Text("Title: ${metadata.title}", style = MaterialTheme.typography.bodySmall)
                  Text("Author: ${metadata.author}", style = MaterialTheme.typography.bodySmall)
                  Text("Pages: ${if (metadata.pageCount > 0) metadata.pageCount else "Unknown"}", style = MaterialTheme.typography.bodySmall)
                  Text("File Size: ${String.format(Locale.US, "%.2f", metadata.fileSizeMb)} MB", style = MaterialTheme.typography.bodySmall)
                  Text("Encryption: ${metadata.encryptionMethod}", style = MaterialTheme.typography.bodySmall)
                  Text("Permissions: ${if (metadata.canPrint) "Printing allowed" else "Printing denied"}, ${if (metadata.canCopy) "Copying allowed" else "Copying denied"}", style = MaterialTheme.typography.bodySmall)
              }
          }
      }
  }
  ```

---

#### File 5: `app/src/main/java/com/example/ui/components/EmptyStateCard.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun EmptyStateCard(
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.CircleShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Description
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Surface
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import com.example.R

  @Composable
  fun EmptyStateCard(
      modifier: Modifier = Modifier
  ) {
      Column(
          modifier = modifier,
          horizontalAlignment = Alignment.CenterHorizontally
      ) {
          Spacer(modifier = Modifier.height(32.dp))
          Surface(
              modifier = Modifier.size(80.dp),
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer
          ) {
              Box(contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = Icons.Default.Description,
                      contentDescription = null,
                      modifier = Modifier.size(40.dp),
                      tint = MaterialTheme.colorScheme.onPrimaryContainer
                  )
              }
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              text = stringResource(R.string.title_empty_state),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
              text = stringResource(R.string.desc_empty_state),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 16.dp)
          )
      }
  }
  ```

---

#### File 6: `app/src/main/java/com/example/ui/components/ThemeDropdownMenu.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun ThemeDropdownMenu(
      onThemeSelected: (ThemeMode) -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.foundation.layout.Box
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Palette
  import androidx.compose.material3.DropdownMenu
  import androidx.compose.material3.DropdownMenuItem
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Modifier
  import com.example.data.ThemeMode

  @Composable
  fun ThemeDropdownMenu(
      onThemeSelected: (ThemeMode) -> Unit,
      modifier: Modifier = Modifier
  ) {
      var expanded by remember { mutableStateOf(false) }

      Box(modifier = modifier) {
          IconButton(onClick = { expanded = true }) {
              Icon(Icons.Default.Palette, contentDescription = "Theme Settings")
          }
          DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false }
          ) {
              DropdownMenuItem(
                  text = { Text("System Default") },
                  onClick = {
                      onThemeSelected(ThemeMode.SYSTEM)
                      expanded = false
                  }
              )
              DropdownMenuItem(
                  text = { Text("Light") },
                  onClick = {
                      onThemeSelected(ThemeMode.LIGHT)
                      expanded = false
                  }
              )
              DropdownMenuItem(
                  text = { Text("Dark") },
                  onClick = {
                      onThemeSelected(ThemeMode.DARK)
                      expanded = false
                  }
              )
              DropdownMenuItem(
                  text = { Text("AMOLED Black") },
                  onClick = {
                      onThemeSelected(ThemeMode.AMOLED)
                      expanded = false
                  }
              )
          }
      }
  }
  ```

---

#### File 7: `app/src/main/java/com/example/ui/components/WhatsNewDialog.kt`
- **Package**: `package com.example.ui.components`
- **Signature**:
  ```kotlin
  @Composable
  fun WhatsNewDialog(
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.ui.components

  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import com.example.R

  @Composable
  fun WhatsNewDialog(
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text(stringResource(R.string.dialog_title_whats_new)) },
          text = { Text(stringResource(R.string.changelog_content)) },
          confirmButton = {
              TextButton(onClick = onDismiss) {
                  Text(stringResource(R.string.btn_close))
              }
          },
          modifier = modifier
      )
  }
  ```

---

#### File 8: `app/src/main/java/com/example/feature/vault/SavePasswordDialog.kt`
- **Package**: `package com.example.feature.vault`
- **Signature**:
  ```kotlin
  @Composable
  fun SavePasswordDialog(
      currentPassword: String,
      onDismiss: () -> Unit,
      onSave: (name: String, pass: String) -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.vault

  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.wrapContentHeight
  import androidx.compose.foundation.layout.wrapContentWidth
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.OutlinedTextField
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.window.DialogProperties
  import com.example.R

  @Composable
  fun SavePasswordDialog(
      currentPassword: String,
      onDismiss: () -> Unit,
      onSave: (name: String, pass: String) -> Unit,
      modifier: Modifier = Modifier
  ) {
      var passwordName by remember { mutableStateOf("") }
      val haptic = LocalHapticFeedback.current
      AlertDialog(
          modifier = modifier
              .padding(24.dp)
              .wrapContentWidth()
              .wrapContentHeight(),
          properties = DialogProperties(usePlatformDefaultWidth = false),
          onDismissRequest = onDismiss,
          title = { Text(stringResource(R.string.dialog_title_save_password)) },
          text = {
              OutlinedTextField(
                  value = passwordName,
                  onValueChange = { passwordName = it },
                  label = { Text(stringResource(R.string.label_password_name_hint)) },
                  singleLine = true
              )
          },
          confirmButton = {
              TextButton(onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  if (passwordName.isNotBlank()) {
                      onSave(passwordName, currentPassword)
                  }
              }) { Text(stringResource(R.string.btn_save)) }
          },
          dismissButton = {
              TextButton(onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onDismiss()
              }) { Text(stringResource(R.string.btn_cancel)) }
          }
      )
  }
  ```

---

#### File 9: `app/src/main/java/com/example/feature/vault/SavedPasswordListDialog.kt`
- **Package**: `package com.example.feature.vault`
- **Signature**:
  ```kotlin
  @Composable
  fun SavedPasswordListDialog(
      savedPasswords: List<PasswordEntity>,
      onDismiss: () -> Unit,
      onSelectPassword: (String) -> Unit,
      onDeletePassword: (PasswordEntity) -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.vault

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.wrapContentHeight
  import androidx.compose.foundation.layout.wrapContentWidth
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Close
  import androidx.compose.material.icons.filled.Delete
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedTextField
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.SpanStyle
  import androidx.compose.ui.text.buildAnnotatedString
  import androidx.compose.ui.text.withStyle
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.window.DialogProperties
  import com.example.R
  import com.example.data.PasswordEntity

  @Composable
  fun SavedPasswordListDialog(
      savedPasswords: List<PasswordEntity>,
      onDismiss: () -> Unit,
      onSelectPassword: (String) -> Unit,
      onDeletePassword: (PasswordEntity) -> Unit,
      modifier: Modifier = Modifier
  ) {
      var searchQuery by remember { mutableStateOf("") }
      val haptic = LocalHapticFeedback.current

      AlertDialog(
          modifier = modifier
              .padding(24.dp)
              .wrapContentWidth()
              .wrapContentHeight(),
          properties = DialogProperties(usePlatformDefaultWidth = false),
          onDismissRequest = onDismiss,
          title = { Text(stringResource(R.string.dialog_title_saved_passwords)) },
          text = {
              if (savedPasswords.isEmpty()) {
                  Text(stringResource(R.string.msg_no_saved_passwords))
              } else {
                  Column {
                      OutlinedTextField(
                          value = searchQuery,
                          onValueChange = { searchQuery = it },
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(bottom = 8.dp),
                          label = { Text("Search") },
                          singleLine = true,
                          trailingIcon = {
                              if (searchQuery.isNotEmpty()) {
                                  IconButton(onClick = { searchQuery = "" }) {
                                      Icon(Icons.Default.Close, contentDescription = "Clear search")
                                  }
                              }
                          }
                      )

                      val filteredPasswords = savedPasswords.filter {
                          it.name.contains(searchQuery, ignoreCase = true)
                      }

                      if (filteredPasswords.isEmpty()) {
                          Text(
                              text = "No matching passwords",
                              modifier = Modifier.padding(top = 8.dp),
                              style = MaterialTheme.typography.bodyMedium
                          )
                      } else {
                          LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                              items(filteredPasswords, key = { it.id }) { savedPass ->
                                  Row(
                                      modifier = Modifier
                                          .fillMaxWidth()
                                          .clickable {
                                              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                              onSelectPassword(savedPass.passwordValue)
                                          }
                                          .padding(vertical = 12.dp, horizontal = 8.dp),
                                      verticalAlignment = Alignment.CenterVertically
                                  ) {
                                      val name = savedPass.name
                                      val highlightIndex = name.indexOf(searchQuery, ignoreCase = true)
                                      val annotatedString = if (searchQuery.isNotEmpty() && highlightIndex != -1) {
                                          buildAnnotatedString {
                                              append(name.substring(0, highlightIndex))
                                              withStyle(
                                                  style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)
                                              ) {
                                                  append(name.substring(highlightIndex, highlightIndex + searchQuery.length))
                                              }
                                              append(name.substring(highlightIndex + searchQuery.length))
                                          }
                                      } else {
                                          buildAnnotatedString { append(name) }
                                      }

                                      Text(
                                          text = annotatedString,
                                          modifier = Modifier.weight(1f),
                                          style = MaterialTheme.typography.bodyLarge
                                      )
                                      IconButton(onClick = {
                                          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                          onDeletePassword(savedPass)
                                      }) {
                                          Icon(
                                              Icons.Default.Delete,
                                              contentDescription = "Delete ${savedPass.name}"
                                          )
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
          },
          confirmButton = {
              TextButton(onClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onDismiss()
              }) { Text(stringResource(R.string.btn_close)) }
          }
      )
  }
  ```

---

#### File 10: `app/src/main/java/com/example/feature/vault/BiometricHelper.kt`
- **Package**: `package com.example.feature.vault`
- **Implementation**:
  ```kotlin
  package com.example.feature.vault

  import android.content.Context
  import androidx.biometric.BiometricManager
  import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
  import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
  import androidx.biometric.BiometricPrompt
  import androidx.core.content.ContextCompat
  import androidx.fragment.app.FragmentActivity
  import com.example.R

  object BiometricHelper {

      fun canAuthenticate(context: Context): Boolean {
          val biometricManager = BiometricManager.from(context)
          return biometricManager.canAuthenticate(
              BIOMETRIC_STRONG or DEVICE_CREDENTIAL
          ) == BiometricManager.BIOMETRIC_SUCCESS
      }

      fun authenticate(
          activity: FragmentActivity,
          title: String = activity.getString(R.string.biometric_prompt_title),
          subtitle: String = activity.getString(R.string.biometric_prompt_subtitle),
          onSuccess: () -> Unit,
          onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> }
      ) {
          if (!canAuthenticate(activity)) {
              onSuccess()
              return
          }

          val executor = ContextCompat.getMainExecutor(activity)
          val biometricPrompt = BiometricPrompt(
              activity,
              executor,
              object : BiometricPrompt.AuthenticationCallback() {
                  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                      super.onAuthenticationError(errorCode, errString)
                      onError(errorCode, errString)
                  }

                  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                      super.onAuthenticationSucceeded(result)
                      onSuccess()
                  }
              }
          )

          val promptInfo = BiometricPrompt.PromptInfo.Builder()
              .setTitle(title)
              .setSubtitle(subtitle)
              .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
              .build()

          biometricPrompt.authenticate(promptInfo)
      }
  }
  ```

---

#### File 11: `app/src/main/java/com/example/feature/decrypt/BatchProgressDialog.kt`
- **Package**: `package com.example.feature.decrypt`
- **Signature**:
  ```kotlin
  @Composable
  fun BatchProgressDialog(
      progress: Int,
      total: Int,
      onCancel: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.decrypt

  import androidx.compose.animation.core.animateFloatAsState
  import androidx.compose.animation.core.tween
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.LinearProgressIndicator
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.unit.dp
  import com.example.R

  @Composable
  fun BatchProgressDialog(
      progress: Int,
      total: Int,
      onCancel: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      AlertDialog(
          onDismissRequest = { /* No dismiss by clicking outside */ },
          title = { Text("Processing Batch") },
          text = {
              Column(modifier = Modifier.fillMaxWidth()) {
                  Text(
                      "$progress of $total completed",
                      modifier = Modifier.padding(bottom = 16.dp),
                      style = MaterialTheme.typography.bodyLarge
                  )
                  val animatedProgress by animateFloatAsState(
                      targetValue = if (total > 0) progress.toFloat() / total else 0f,
                      animationSpec = tween(durationMillis = 300),
                      label = "BatchProgressAnimation"
                  )
                  LinearProgressIndicator(
                      progress = { animatedProgress },
                      modifier = Modifier
                          .fillMaxWidth()
                          .height(8.dp),
                      color = MaterialTheme.colorScheme.primary,
                      trackColor = MaterialTheme.colorScheme.surfaceVariant
                  )
              }
          },
          confirmButton = {
              TextButton(onClick = onCancel) {
                  Text(stringResource(R.string.btn_cancel))
              }
          },
          modifier = modifier
      )
  }
  ```

---

#### File 12: `app/src/main/java/com/example/feature/decrypt/AutoUnlockPasswordDialog.kt`
- **Package**: `package com.example.feature.decrypt`
- **Signature**:
  ```kotlin
  @Composable
  fun AutoUnlockPasswordDialog(
      fileName: String,
      errorMessage: String?,
      onUnlock: (password: String, rememberPassword: Boolean) -> Unit,
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.decrypt

  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.text.KeyboardActions
  import androidx.compose.foundation.text.KeyboardOptions
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Visibility
  import androidx.compose.material.icons.filled.VisibilityOff
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.Button
  import androidx.compose.material3.Checkbox
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedTextField
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.input.ImeAction
  import androidx.compose.ui.text.input.KeyboardType
  import androidx.compose.ui.text.input.PasswordVisualTransformation
  import androidx.compose.ui.text.input.VisualTransformation
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.window.DialogProperties
  import com.example.R

  @Composable
  fun AutoUnlockPasswordDialog(
      fileName: String,
      errorMessage: String?,
      onUnlock: (password: String, rememberPassword: Boolean) -> Unit,
      onDismiss: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      var inputPass by remember { mutableStateOf("") }
      var passVisible by remember { mutableStateOf(false) }
      var rememberPass by remember { mutableStateOf(true) }

      val unlockAction = {
          if (inputPass.isNotBlank()) {
              onUnlock(inputPass, rememberPass)
          }
      }

      AlertDialog(
          onDismissRequest = onDismiss,
          properties = DialogProperties(dismissOnClickOutside = false),
          title = { Text(stringResource(R.string.title_unlock_pdf)) },
          text = {
              Column(modifier = Modifier.fillMaxWidth()) {
                  Text(
                      text = fileName.ifBlank { "Document" },
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                  OutlinedTextField(
                      value = inputPass,
                      onValueChange = { inputPass = it },
                      label = { Text(stringResource(R.string.label_password)) },
                      singleLine = true,
                      isError = errorMessage != null,
                      supportingText = if (errorMessage != null) {
                          { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                      } else null,
                      visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                      keyboardOptions = KeyboardOptions(
                          keyboardType = KeyboardType.Password,
                          imeAction = ImeAction.Done
                      ),
                      keyboardActions = KeyboardActions(onDone = { unlockAction() }),
                      trailingIcon = {
                          val img = if (passVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                          IconButton(onClick = { passVisible = !passVisible }) {
                              Icon(img, contentDescription = if (passVisible) "Hide password" else "Show password")
                          }
                      },
                      modifier = Modifier.fillMaxWidth()
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable { rememberPass = !rememberPass }
                  ) {
                      Checkbox(
                          checked = rememberPass,
                          onCheckedChange = { rememberPass = it }
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                          text = stringResource(R.string.label_remember_password),
                          style = MaterialTheme.typography.bodyMedium
                      )
                  }
              }
          },
          confirmButton = {
              Button(
                  onClick = unlockAction,
                  enabled = inputPass.isNotBlank()
              ) {
                  Text(stringResource(R.string.btn_unlock_pdf))
              }
          },
          dismissButton = {
              TextButton(onClick = onDismiss) {
                  Text(stringResource(R.string.btn_cancel))
              }
          },
          modifier = modifier
      )
  }
  ```

---

#### File 13: `app/src/main/java/com/example/feature/viewer/PdfViewerDialog.kt`
- **Package**: `package com.example.feature.viewer`
- **Signature**:
  ```kotlin
  @Composable
  fun PdfViewerDialog(
      uri: Uri,
      modifier: Modifier = Modifier,
      title: String = "",
      onDismiss: () -> Unit,
      onShare: (() -> Unit)? = null,
      onSaveAs: (() -> Unit)? = null
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.viewer

  import android.net.Uri
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.window.Dialog
  import androidx.compose.ui.window.DialogProperties

  @Composable
  fun PdfViewerDialog(
      uri: Uri,
      modifier: Modifier = Modifier,
      title: String = "",
      onDismiss: () -> Unit,
      onShare: (() -> Unit)? = null,
      onSaveAs: (() -> Unit)? = null
  ) {
      Dialog(
          onDismissRequest = onDismiss,
          properties = DialogProperties(
              usePlatformDefaultWidth = false,
              decorFitsSystemWindows = false
          )
      ) {
          PdfViewerScreen(
              uri = uri,
              modifier = modifier,
              title = title,
              onClose = onDismiss,
              onShare = onShare,
              onSaveAs = onSaveAs
          )
      }
  }
  ```

---

#### File 14: `app/src/main/java/com/example/feature/viewer/PdfViewerScreen.kt`
- **Package**: `package com.example.feature.viewer`
- **Signature**:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PdfViewerScreen(
      uri: Uri,
      modifier: Modifier = Modifier,
      title: String = "",
      onClose: () -> Unit,
      onShare: (() -> Unit)? = null,
      onSaveAs: (() -> Unit)? = null
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.viewer

  import android.graphics.Bitmap
  import android.graphics.pdf.PdfRenderer
  import android.net.Uri
  import android.os.ParcelFileDescriptor
  import android.util.LruCache
  import androidx.compose.foundation.background
  import androidx.compose.foundation.gestures.detectTransformGestures
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.WindowInsets
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.navigationBarsPadding
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.safeDrawing
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.rememberLazyListState
  import androidx.compose.foundation.shape.CircleShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Close
  import androidx.compose.material.icons.filled.Save
  import androidx.compose.material.icons.filled.Share
  import androidx.compose.material3.Button
  import androidx.compose.material3.CircularProgressIndicator
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Surface
  import androidx.compose.material3.Text
  import androidx.compose.material3.TopAppBar
  import androidx.compose.material3.TopAppBarDefaults
  import androidx.compose.material3.surfaceColorAtElevation
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.DisposableEffect
  import androidx.compose.runtime.derivedStateOf
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableFloatStateOf
  import androidx.compose.runtime.mutableIntStateOf
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.graphicsLayer
  import androidx.compose.ui.input.pointer.pointerInput
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.example.R
  import com.example.util.FileUtils

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PdfViewerScreen(
      uri: Uri,
      modifier: Modifier = Modifier,
      title: String = "",
      onClose: () -> Unit,
      onShare: (() -> Unit)? = null,
      onSaveAs: (() -> Unit)? = null
  ) {
      val context = LocalContext.current
      var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
      var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
      var pageCount by remember { mutableIntStateOf(0) }
      var errorMessage by remember { mutableStateOf<String?>(null) }
      var isLoading by remember { mutableStateOf(true) }

      // 12.5% of max heap for PDF bitmap caching
      val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
      val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
      val pageBitmapCache = remember {
          object : LruCache<Int, Bitmap>(cacheSize) {
              override fun sizeOf(key: Int, value: Bitmap): Int {
                  return value.byteCount / 1024
              }
          }
      }

      DisposableEffect(uri) {
          var tempFile: java.io.File? = null
          try {
              val fileDescriptor: ParcelFileDescriptor? = try {
                  context.contentResolver.openFileDescriptor(uri, "r")
              } catch (_: Exception) {
                  null
              } ?: run {
                  val temp = java.io.File(context.cacheDir, "preview_temp_${System.currentTimeMillis()}.pdf")
                  val inputStream = try {
                      context.contentResolver.openInputStream(uri)
                  } catch (_: Exception) {
                      try {
                          if (uri.scheme == "file" && uri.path != null) {
                              java.io.FileInputStream(java.io.File(uri.path!!))
                          } else null
                      } catch (_: Exception) {
                          null
                      }
                  }

                  inputStream?.use { input ->
                      temp.outputStream().use { output -> input.copyTo(output) }
                  }
                  tempFile = temp
                  if (temp.exists() && temp.length() > 0) {
                      ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                  } else {
                      null
                  }
              }

              if (fileDescriptor != null) {
                  pfd = fileDescriptor
                  val pdfRenderer = PdfRenderer(fileDescriptor)
                  renderer = pdfRenderer
                  pageCount = pdfRenderer.pageCount
                  isLoading = false
              } else {
                  errorMessage = context.getString(R.string.error_pdf_preview)
                  isLoading = false
              }
          } catch (e: Exception) {
              errorMessage = e.localizedMessage ?: context.getString(R.string.error_pdf_preview)
              isLoading = false
          }

          onDispose {
              try {
                  renderer?.close()
              } catch (_: Exception) {}
              try {
                  pfd?.close()
              } catch (_: Exception) {}
              FileUtils.secureDelete(tempFile)
              val snapshot = pageBitmapCache.snapshot()
              pageBitmapCache.evictAll()
              snapshot.values.forEach { bmp ->
                  if (bmp != null && !bmp.isRecycled) {
                      bmp.recycle()
                  }
              }
          }
      }

      val listState = rememberLazyListState()
      val currentPage by remember {
          derivedStateOf {
              if (pageCount > 0) (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount) else 1
          }
      }

      var scale by remember { mutableFloatStateOf(1f) }
      var offsetX by remember { mutableFloatStateOf(0f) }
      var offsetY by remember { mutableFloatStateOf(0f) }

      Scaffold(
          modifier = modifier,
          contentWindowInsets = WindowInsets.safeDrawing,
          topBar = {
              TopAppBar(
                  title = {
                      Column {
                          Text(
                              text = title.ifEmpty { "PDF Viewer" },
                              style = MaterialTheme.typography.titleMedium,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                          )
                          if (pageCount > 0) {
                              Text(
                                  text = stringResource(R.string.label_page_indicator, currentPage, pageCount),
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                          }
                      }
                  },
                  navigationIcon = {
                      IconButton(onClick = onClose) {
                          Icon(
                              imageVector = Icons.Default.Close,
                              contentDescription = stringResource(R.string.btn_close)
                          )
                      }
                  },
                  actions = {
                      if (onSaveAs != null) {
                          IconButton(onClick = onSaveAs) {
                              Icon(
                                  imageVector = Icons.Default.Save,
                                  contentDescription = stringResource(R.string.btn_save_as)
                              )
                          }
                      }
                      if (onShare != null) {
                          IconButton(onClick = onShare) {
                              Icon(
                                  imageVector = Icons.Default.Share,
                                  contentDescription = stringResource(R.string.btn_share_file)
                              )
                          }
                      }
                  },
                  colors = TopAppBarDefaults.topAppBarColors(
                      containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                  )
              )
          }
      ) { innerPadding ->
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(innerPadding)
                  .background(MaterialTheme.colorScheme.background)
          ) {
              when {
                  isLoading -> {
                      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                          CircularProgressIndicator()
                      }
                  }
                  errorMessage != null -> {
                      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                          Column(
                              horizontalAlignment = Alignment.CenterHorizontally,
                              verticalArrangement = Arrangement.Center,
                              modifier = Modifier.padding(24.dp)
                          ) {
                              Text(
                                  text = errorMessage ?: stringResource(R.string.error_pdf_preview),
                                  color = MaterialTheme.colorScheme.error,
                                  style = MaterialTheme.typography.bodyLarge
                              )
                              Spacer(modifier = Modifier.height(16.dp))
                              Button(onClick = onClose) {
                                  Text(stringResource(R.string.btn_close))
                              }
                          }
                      }
                  }
                  renderer != null && pageCount > 0 -> {
                      Box(
                          modifier = Modifier
                              .fillMaxSize()
                              .pointerInput(Unit) {
                                  detectTransformGestures { _, pan, zoom, _ ->
                                      scale = (scale * zoom).coerceIn(1f, 4f)
                                      if (scale > 1f) {
                                          offsetX += pan.x
                                          offsetY += pan.y
                                      } else {
                                          offsetX = 0f
                                          offsetY = 0f
                                      }
                                  }
                              }
                              .graphicsLayer {
                                  scaleX = scale
                                  scaleY = scale
                                  translationX = offsetX
                                  translationY = offsetY
                              }
                      ) {
                          LazyColumn(
                              state = listState,
                              modifier = Modifier.fillMaxSize(),
                              contentPadding = PaddingValues(
                                  start = 16.dp,
                                  end = 16.dp,
                                  top = 16.dp,
                                  bottom = 88.dp
                              ),
                              verticalArrangement = Arrangement.spacedBy(16.dp),
                              horizontalAlignment = Alignment.CenterHorizontally
                          ) {
                              items(
                                  count = pageCount,
                                  key = { it }
                              ) { pageIndex ->
                                  PdfPageItem(
                                      renderer = renderer!!,
                                      pageIndex = pageIndex,
                                      cache = pageBitmapCache
                                  )
                              }
                          }
                      }

                      Surface(
                          shape = CircleShape,
                          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                          shadowElevation = 6.dp,
                          modifier = Modifier
                              .align(Alignment.BottomCenter)
                              .navigationBarsPadding()
                              .padding(bottom = 24.dp)
                      ) {
                          Text(
                              text = stringResource(R.string.label_page_indicator, currentPage, pageCount),
                              style = MaterialTheme.typography.labelMedium,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                          )
                      }
                  }
              }
          }
      }
  }
  ```

---

#### File 15: `app/src/main/java/com/example/feature/viewer/PdfPageItem.kt`
- **Package**: `package com.example.feature.viewer`
- **Signature**:
  ```kotlin
  @Composable
  fun PdfPageItem(
      renderer: PdfRenderer,
      pageIndex: Int,
      cache: LruCache<Int, Bitmap>,
      modifier: Modifier = Modifier
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.viewer

  import android.graphics.Bitmap
  import android.graphics.Canvas
  import android.graphics.Color
  import android.graphics.pdf.PdfRenderer
  import android.util.LruCache
  import androidx.compose.foundation.Image
  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.aspectRatio
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material3.CircularProgressIndicator
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.draw.shadow
  import androidx.compose.ui.graphics.asImageBitmap
  import androidx.compose.ui.layout.ContentScale
  import androidx.compose.ui.platform.LocalDensity
  import androidx.compose.ui.unit.dp
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext

  @Composable
  fun PdfPageItem(
      renderer: PdfRenderer,
      pageIndex: Int,
      cache: LruCache<Int, Bitmap>,
      modifier: Modifier = Modifier
  ) {
      var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(cache.get(pageIndex)) }
      val density = LocalDensity.current

      LaunchedEffect(pageIndex) {
          if (pageBitmap == null) {
              val bitmap = withContext(Dispatchers.IO) {
                  try {
                      synchronized(renderer) {
                          val page = renderer.openPage(pageIndex)
                          val screenDensity = density.density
                          val width = (page.width * screenDensity * 1.5f).toInt().coerceAtLeast(1)
                          val height = (page.height * screenDensity * 1.5f).toInt().coerceAtLeast(1)

                          val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                          val canvas = Canvas(bmp)
                          canvas.drawColor(Color.WHITE)
                          page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                          page.close()
                          cache.put(pageIndex, bmp)
                          bmp
                      }
                  } catch (_: Exception) {
                      null
                  }
              }
              pageBitmap = bitmap
          }
      }

      Box(
          modifier = modifier
              .fillMaxWidth()
              .shadow(4.dp, RoundedCornerShape(8.dp))
              .clip(RoundedCornerShape(8.dp))
              .background(androidx.compose.ui.graphics.Color.White),
          contentAlignment = Alignment.Center
      ) {
          if (pageBitmap != null) {
              Image(
                  bitmap = pageBitmap!!.asImageBitmap(),
                  contentDescription = "Page ${pageIndex + 1}",
                  contentScale = ContentScale.FillWidth,
                  modifier = Modifier.fillMaxWidth()
              )
          } else {
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(0.707f)
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                  contentAlignment = Alignment.Center
              ) {
                  CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
              }
          }
      }
  }
  ```

---

#### File 16: `app/src/main/java/com/example/feature/decrypt/PDFDecryptorScreen.kt`
- **Package**: `package com.example.feature.decrypt`
- **Signature**:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PDFDecryptorScreen(
      viewModel: MainViewModel,
      modifier: Modifier = Modifier,
      snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
      windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
  )
  ```
- **Implementation**:
  ```kotlin
  package com.example.feature.decrypt

  import android.app.Activity
  import android.content.Context
  import android.content.Intent
  import android.net.Uri
  import android.os.Build
  import android.view.HapticFeedbackConstants
  import android.view.WindowManager
  import androidx.activity.compose.rememberLauncherForActivityResult
  import androidx.activity.result.contract.ActivityResultContracts
  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.draganddrop.dragAndDropTarget
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Folder
  import androidx.compose.material.icons.filled.Share
  import androidx.compose.material3.Button
  import androidx.compose.material3.ButtonDefaults
  import androidx.compose.material3.CircularProgressIndicator
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.OutlinedButton
  import androidx.compose.material3.SnackbarDuration
  import androidx.compose.material3.SnackbarHostState
  import androidx.compose.material3.SnackbarResult
  import androidx.compose.material3.Surface
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.DisposableEffect
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.rememberCoroutineScope
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draganddrop.DragAndDropEvent
  import androidx.compose.ui.draganddrop.DragAndDropTarget
  import androidx.compose.ui.draganddrop.toAndroidDragEvent
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.platform.LocalView
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.window.Dialog
  import androidx.compose.ui.window.DialogProperties
  import androidx.fragment.app.FragmentActivity
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.example.MainViewModel
  import com.example.R
  import com.example.feature.vault.BiometricHelper
  import com.example.feature.vault.SavePasswordDialog
  import com.example.feature.vault.SavedPasswordListDialog
  import com.example.feature.viewer.PdfViewerDialog
  import com.example.ui.components.DocumentDetailsCard
  import com.example.ui.components.EmptyStateCard
  import com.example.ui.components.PasswordInputSection
  import com.example.ui.components.SelectedFilesCard
  import com.example.ui.components.ThemeDropdownMenu
  import com.example.ui.components.WhatsNewDialog
  import kotlinx.coroutines.launch

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PDFDecryptorScreen(
      viewModel: MainViewModel,
      modifier: Modifier = Modifier,
      snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
      windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
  ) {
      val scope = rememberCoroutineScope()
      val selectedUris by viewModel.selectedUris.collectAsStateWithLifecycle()
      val selectedFileNames by viewModel.selectedFileNames.collectAsStateWithLifecycle()
      val selectedMetadata by viewModel.selectedMetadata.collectAsStateWithLifecycle()
      val batchState by viewModel.batchState.collectAsStateWithLifecycle()
      val password by viewModel.password.collectAsStateWithLifecycle()
      val passwordVisible by viewModel.passwordVisible.collectAsStateWithLifecycle()
      val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
      val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
      val savedPasswords by viewModel.savedPasswords.collectAsStateWithLifecycle()
      val lastDecryptedUri by viewModel.lastDecryptedUri.collectAsStateWithLifecycle()
      val previewPdfUri by viewModel.previewPdfUri.collectAsStateWithLifecycle()
      val isAutoUnlocking by viewModel.isAutoUnlocking.collectAsStateWithLifecycle()
      val showAutoUnlockPasswordPrompt by viewModel.showAutoUnlockPasswordPrompt.collectAsStateWithLifecycle()
      val autoUnlockTargetUri by viewModel.autoUnlockTargetUri.collectAsStateWithLifecycle()
      val autoUnlockFileName by viewModel.autoUnlockFileName.collectAsStateWithLifecycle()
      val autoUnlockErrorMessage by viewModel.autoUnlockErrorMessage.collectAsStateWithLifecycle()

      val context = LocalContext.current
      val showSavePasswordDialog by viewModel.showSavePasswordDialog.collectAsStateWithLifecycle()
      val showPasswordListDialog by viewModel.showPasswordListDialog.collectAsStateWithLifecycle()
      var showWhatsNewDialog by remember { mutableStateOf(false) }

      val versionName = remember {
          try {
              context.packageManager.getPackageInfo(context.packageName, 0).versionName
          } catch (e: Exception) {
              "0.1.0"
          }
      }

      LaunchedEffect(versionName) {
          val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
          val lastSeenVersion = sharedPrefs.getString("last_seen_version", null)

          if (lastSeenVersion != versionName) {
              showWhatsNewDialog = true
              sharedPrefs.edit().putString("last_seen_version", versionName).apply()
          }
      }

      val isSecureModeActive = showPasswordListDialog || showSavePasswordDialog
      val activity = context as? Activity
      DisposableEffect(isSecureModeActive) {
          if (isSecureModeActive) {
              activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
          } else {
              activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
          onDispose {
              activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
      }

      val haptic = LocalHapticFeedback.current
      val view = LocalView.current

      val openDocumentLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult(),
          onResult = { result ->
              if (result.resultCode == Activity.RESULT_OK) {
                  val uris = mutableListOf<Uri>()
                  result.data?.clipData?.let { clipData ->
                      for (i in 0 until clipData.itemCount) {
                          uris.add(clipData.getItemAt(i).uri)
                      }
                  } ?: result.data?.data?.let { uris.add(it) }

                  if (uris.isNotEmpty()) {
                      val flags = result.data?.flags ?: 0
                      val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                      if (takeFlags != 0) {
                          uris.forEach { uri ->
                              try {
                                  @Suppress("WrongConstant")
                                  context.contentResolver.takePersistableUriPermission(
                                      uri,
                                      takeFlags
                                  )
                              } catch (e: Exception) {
                                  e.printStackTrace()
                              }
                          }
                      }
                      viewModel.setSelectedUris(context, uris)
                  }
              }
          }
      )

      val requestOpenDocumentPicker by viewModel.requestOpenDocumentPicker.collectAsStateWithLifecycle()
      LaunchedEffect(requestOpenDocumentPicker) {
          if (requestOpenDocumentPicker) {
              val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                  addCategory(Intent.CATEGORY_OPENABLE)
                  type = "application/pdf"
                  putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
              }
              openDocumentLauncher.launch(intent)
              viewModel.onDocumentPickerLaunched()
          }
      }

      val openDocumentTreeLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult(),
          onResult = { result ->
              if (result.resultCode == Activity.RESULT_OK) {
                  val destUri = result.data?.data
                  if (destUri != null) {
                      viewModel.decryptMultiplePdfs(context, selectedUris, destUri, password, "", false)
                  }
              }
          }
      )

      var isDragging by remember { mutableStateOf(false) }

      val saveDecryptedPdfLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult(),
          onResult = { result ->
              if (result.resultCode == Activity.RESULT_OK) {
                  val destUri = result.data?.data
                  val sourceUri = previewPdfUri ?: lastDecryptedUri
                  if (destUri != null && sourceUri != null) {
                      viewModel.copyUriStream(context, sourceUri, destUri)
                  }
              }
          }
      )

      val dragAndDropTarget = remember {
          object : DragAndDropTarget {
              override fun onStarted(event: DragAndDropEvent) {}
              override fun onEntered(event: DragAndDropEvent) {
                  isDragging = true
              }
              override fun onExited(event: DragAndDropEvent) {
                  isDragging = false
              }
              override fun onEnded(event: DragAndDropEvent) {
                  isDragging = false
              }
              override fun onDrop(event: DragAndDropEvent): Boolean {
                  isDragging = false
                  val dragEvent = event.toAndroidDragEvent()
                  val clipData = dragEvent.clipData ?: return false
                  val act = context as? Activity
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                      act?.requestDragAndDropPermissions(dragEvent)
                  }
                  val uris = mutableListOf<Uri>()
                  for (i in 0 until clipData.itemCount) {
                      val uri = clipData.getItemAt(i).uri
                      if (uri != null) {
                          uris.add(uri)
                      }
                  }
                  if (uris.isNotEmpty()) {
                      viewModel.setSelectedUris(context, uris)
                      return true
                  }
                  return false
              }
          }
      }

      LaunchedEffect(statusMessage) {
          statusMessage?.let { msg ->
              if (msg.startsWith("Error") || msg.startsWith("Failed") || msg.contains("❌")) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                      view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                  } else {
                      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                  }
              } else if (msg.contains("✅") || msg.contains("Decrypted & Saved")) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                      view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                  } else {
                      view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                  }
              }
          }
      }

      val createDocumentLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult(),
          onResult = { result ->
              if (result.resultCode == Activity.RESULT_OK) {
                  val destUri = result.data?.data
                  if (destUri != null) {
                      viewModel.decryptAndSaveAs(context, selectedUris.firstOrNull(), destUri, password)
                  }
              }
          }
      )

      Box(
          modifier = modifier
              .fillMaxSize()
              .dragAndDropTarget(
                  shouldStartDragAndDrop = { event ->
                      val dragEvent = event.toAndroidDragEvent()
                      val clipDescription = dragEvent.clipDescription
                      clipDescription != null && clipDescription.hasMimeType("application/pdf")
                  },
                  target = dragAndDropTarget
              )
              .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
              .padding(24.dp)
      ) {
          Column(
              modifier = Modifier
                  .fillMaxSize()
                  .padding(bottom = 48.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
          ) {
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(bottom = 24.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Spacer(modifier = Modifier.width(48.dp))
                  Text(
                      text = stringResource(R.string.app_name),
                      style = MaterialTheme.typography.headlineLarge,
                      color = MaterialTheme.colorScheme.primary
                  )
                  ThemeDropdownMenu(
                      onThemeSelected = { mode -> viewModel.setTheme(mode) }
                  )
              }

              Button(
                  onClick = {
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                          addCategory(Intent.CATEGORY_OPENABLE)
                          type = "application/pdf"
                          putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                      }
                      openDocumentLauncher.launch(intent)
                  },
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(56.dp)
              ) {
                  Icon(Icons.Default.Folder, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(stringResource(R.string.btn_select_pdfs))
              }

              if (selectedUris.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(16.dp))
                  SelectedFilesCard(
                      fileNames = selectedFileNames,
                      fileCount = selectedUris.size,
                      onClear = { viewModel.setSelectedUris(context, emptyList()) }
                  )

                  if (selectedMetadata != null) {
                      Spacer(modifier = Modifier.height(12.dp))
                      DocumentDetailsCard(
                          metadata = selectedMetadata!!,
                          onPreview = if (selectedUris.isNotEmpty()) {
                              { viewModel.previewPdfUri.value = selectedUris.first() }
                          } else null
                      )
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  PasswordInputSection(
                      password = password,
                      passwordVisible = passwordVisible,
                      onPasswordChange = { viewModel.password.value = it },
                      onTogglePasswordVisible = { viewModel.passwordVisible.value = !passwordVisible },
                      onOpenPasswordList = {
                          val fragmentActivity = context as? FragmentActivity
                          if (fragmentActivity != null) {
                              BiometricHelper.authenticate(
                                  activity = fragmentActivity,
                                  title = context.getString(R.string.biometric_prompt_title),
                                  subtitle = context.getString(R.string.biometric_prompt_subtitle),
                                  onSuccess = { viewModel.showPasswordListDialog.value = true },
                                  onError = { errorCode, errString ->
                                      if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                          errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                      ) {
                                          android.widget.Toast.makeText(context, errString, android.widget.Toast.LENGTH_SHORT).show()
                                      }
                                  }
                              )
                          } else {
                              viewModel.showPasswordListDialog.value = true
                          }
                      },
                      onOpenSavePassword = { viewModel.showSavePasswordDialog.value = true }
                  )

                  Spacer(modifier = Modifier.height(24.dp))

                  if (isProcessing && !batchState.isProcessing) {
                      CircularProgressIndicator(
                          modifier = Modifier.size(48.dp),
                          color = MaterialTheme.colorScheme.primary,
                          strokeWidth = 4.dp
                      )
                  } else if (!isProcessing) {
                      Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                          Button(
                              onClick = {
                                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                  viewModel.decryptAndOverwrite(context, selectedUris, password)
                              },
                              modifier = Modifier
                                  .weight(1f)
                                  .height(56.dp),
                              enabled = password.isNotEmpty(),
                              colors = ButtonDefaults.buttonColors(
                                  containerColor = MaterialTheme.colorScheme.errorContainer,
                                  contentColor = MaterialTheme.colorScheme.onErrorContainer
                              )
                          ) {
                              Text(stringResource(R.string.btn_overwrite), textAlign = TextAlign.Center)
                          }

                          Button(
                              onClick = {
                                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                  if (selectedUris.size > 1) {
                                      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                      openDocumentTreeLauncher.launch(intent)
                                  } else {
                                      val fileName = selectedFileNames.firstOrNull() ?: "decrypted.pdf"
                                      val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                          addCategory(Intent.CATEGORY_OPENABLE)
                                          type = "application/pdf"
                                          putExtra(Intent.EXTRA_TITLE, fileName)
                                      }
                                      createDocumentLauncher.launch(intent)
                                  }
                              },
                              modifier = Modifier
                                  .weight(1f)
                                  .height(56.dp),
                              enabled = password.isNotEmpty(),
                              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                          ) {
                              Text(stringResource(R.string.btn_save_as), textAlign = TextAlign.Center)
                          }
                      }
                  }

                  if (statusMessage != null) {
                      Spacer(modifier = Modifier.height(24.dp))
                      Text(
                          text = statusMessage!!,
                          color = if (statusMessage!!.startsWith("Error") || statusMessage!!.startsWith("Failed") || statusMessage!!.contains("❌"))
                              MaterialTheme.colorScheme.error
                          else
                              MaterialTheme.colorScheme.primary,
                          style = MaterialTheme.typography.bodyLarge,
                          textAlign = TextAlign.Center
                      )

                      Spacer(modifier = Modifier.height(24.dp))

                      val previewUri = lastDecryptedUri ?: (if (selectedMetadata?.isEncrypted == false) selectedUris.firstOrNull() else null)
                      if (previewUri != null) {
                          Button(
                              onClick = {
                                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                  viewModel.previewPdfUri.value = previewUri
                              },
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .height(48.dp),
                              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                          ) {
                              Text("👁️")
                              Spacer(modifier = Modifier.width(6.dp))
                              Text(stringResource(R.string.btn_preview_pdf), fontWeight = FontWeight.Bold)
                          }

                          Spacer(modifier = Modifier.height(8.dp))
                      }

                      Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                      ) {
                          OutlinedButton(
                              onClick = {
                                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                  val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                  intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                  try {
                                      context.startActivity(intent)
                                  } catch (e: Exception) {
                                      val fallback = Intent(Intent.ACTION_VIEW)
                                      fallback.setDataAndType(Uri.parse("content://"), "*/*")
                                      try {
                                          context.startActivity(fallback)
                                      } catch (_: Exception) {}
                                  }
                              },
                              modifier = Modifier
                                  .weight(1f)
                                  .height(48.dp)
                          ) {
                              Text("📂")
                              Spacer(modifier = Modifier.width(4.dp))
                              Text(stringResource(R.string.btn_open_file_manager), textAlign = TextAlign.Center)
                          }

                          if (lastDecryptedUri != null) {
                              OutlinedButton(
                                  onClick = {
                                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                      val intent = Intent(Intent.ACTION_VIEW).apply {
                                          setDataAndType(lastDecryptedUri, "application/pdf")
                                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                      }
                                      try {
                                          context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_open_pdf)))
                                      } catch (_: Exception) {}
                                  },
                                  modifier = Modifier
                                      .weight(1f)
                                      .height(48.dp)
                              ) {
                                  Text("📄")
                                  Spacer(modifier = Modifier.width(4.dp))
                                  Text(stringResource(R.string.btn_open_pdf), textAlign = TextAlign.Center)
                              }

                              IconButton(
                                  onClick = {
                                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                      val intent = Intent(Intent.ACTION_SEND).apply {
                                          type = "application/pdf"
                                          putExtra(Intent.EXTRA_STREAM, lastDecryptedUri)
                                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                      }
                                      try {
                                          context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                                      } catch (_: Exception) {}
                                  },
                                  modifier = Modifier.size(48.dp)
                              ) {
                                  Icon(Icons.Default.Share, contentDescription = stringResource(R.string.btn_share_file))
                              }
                          }
                      }
                  }
              } else {
                  EmptyStateCard()
              }
          }

          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .align(Alignment.BottomCenter),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
              Text(
                  text = stringResource(R.string.version_info, versionName ?: "0.1.0"),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.clickable { showWhatsNewDialog = true }
              )
              TextButton(
                  onClick = {
                      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Max97k/PDDF"))
                      try {
                          context.startActivity(intent)
                      } catch (_: Exception) {}
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
              ) {
                  Text(
                      text = stringResource(R.string.github_link),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.primary
                  )
              }
          }
      }

      if (showSavePasswordDialog) {
          SavePasswordDialog(
              currentPassword = password,
              onDismiss = { viewModel.showSavePasswordDialog.value = false },
              onSave = { name, pass ->
                  viewModel.savePassword(name, pass)
                  viewModel.showSavePasswordDialog.value = false
              }
          )
      }

      if (showPasswordListDialog) {
          SavedPasswordListDialog(
              savedPasswords = savedPasswords,
              onDismiss = { viewModel.showPasswordListDialog.value = false },
              onSelectPassword = { selectedPass ->
                  viewModel.password.value = selectedPass
                  viewModel.showPasswordListDialog.value = false
              },
              onDeletePassword = { savedPass ->
                  viewModel.deletePassword(savedPass.id)
                  scope.launch {
                      val result = snackbarHostState.showSnackbar(
                          message = context.getString(R.string.msg_password_deleted),
                          actionLabel = context.getString(R.string.btn_undo),
                          duration = SnackbarDuration.Short
                      )
                      if (result == SnackbarResult.ActionPerformed) {
                          viewModel.restorePassword(savedPass)
                      }
                  }
              }
          )
      }

      if (batchState.isProcessing) {
          BatchProgressDialog(
              progress = batchState.progress,
              total = batchState.total,
              onCancel = { viewModel.cancelBatch() }
          )
      }

      if (showWhatsNewDialog) {
          WhatsNewDialog(
              onDismiss = { showWhatsNewDialog = false }
          )
      }

      if (isAutoUnlocking) {
          Dialog(
              onDismissRequest = { },
              properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
          ) {
              Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surface,
                  tonalElevation = 6.dp
              ) {
                  Column(
                      modifier = Modifier.padding(24.dp),
                      horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                      CircularProgressIndicator()
                      Spacer(modifier = Modifier.height(16.dp))
                      Text(
                          text = stringResource(R.string.label_auto_unlocking),
                          style = MaterialTheme.typography.bodyMedium,
                          textAlign = TextAlign.Center
                      )
                  }
              }
          }
      }

      if (showAutoUnlockPasswordPrompt && autoUnlockTargetUri != null) {
          AutoUnlockPasswordDialog(
              fileName = autoUnlockFileName,
              errorMessage = autoUnlockErrorMessage,
              onUnlock = { inputPass, rememberPass ->
                  viewModel.unlockWithManualPassword(
                      context = context,
                      uri = autoUnlockTargetUri!!,
                      enteredPassword = inputPass,
                      rememberPassword = rememberPass
                  )
              },
              onDismiss = { viewModel.dismissAutoUnlockPrompt() }
          )
      }

      if (previewPdfUri != null) {
          val displayTitle = autoUnlockFileName.ifBlank {
              selectedFileNames.firstOrNull() ?: previewPdfUri?.lastPathSegment?.substringAfterLast('/') ?: stringResource(R.string.app_name)
          }
          PdfViewerDialog(
              uri = previewPdfUri!!,
              title = displayTitle,
              onDismiss = { viewModel.previewPdfUri.value = null },
              onShare = {
                  val intent = Intent(Intent.ACTION_SEND).apply {
                      type = "application/pdf"
                      putExtra(Intent.EXTRA_STREAM, previewPdfUri)
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  try {
                      context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share_file)))
                  } catch (_: Exception) {}
              },
              onSaveAs = {
                  val fileName = "decrypted_${displayTitle.ifBlank { "document.pdf" }}"
                  val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                      addCategory(Intent.CATEGORY_OPENABLE)
                      type = "application/pdf"
                      putExtra(Intent.EXTRA_TITLE, fileName)
                  }
                  saveDecryptedPdfLauncher.launch(intent)
              }
          )
      }
  }
  ```

---

## 5. Verification Method

To independently verify the architecture refactoring:
1. **Compilation Check**: Verify all 16 extracted/updated Kotlin files compile with zero unresolved references.
2. **Package Boundaries Verification**: Confirm:
   - `com.example.ui.components` contains: `SelectedFilesCard`, `PasswordInputSection`, `DocumentDetailsCard`, `EmptyStateCard`, `ThemeDropdownMenu`, `WhatsNewDialog`.
   - `com.example.feature.vault` contains: `SavePasswordDialog`, `SavedPasswordListDialog`, `BiometricHelper`.
   - `com.example.feature.viewer` contains: `PdfViewerDialog`, `PdfViewerScreen`, `PdfPageItem`.
   - `com.example.feature.decrypt` contains: `PDFDecryptorScreen`, `BatchProgressDialog`, `AutoUnlockPasswordDialog`.
   - `com.example.MainActivity` is reduced to ~90 lines.
3. **Execution of Unit & Screenshot Test Suite**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   Ensure tests in `MainActivityTest`, `ComposeUiTests`, `PDFDecryptorScreenshotTest`, and `MultiDeviceScreenshotTest` pass cleanly.
