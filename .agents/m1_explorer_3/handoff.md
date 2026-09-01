# Milestone 1: Verification & Test Impact Analysis Report

## Summary
This report analyzes the test suite impact and threading safety fixes for **Milestone 1 (Architecture & UI Modularization)**. It maps all required test updates when transitioning `MainViewModel` to unified `MainUiState` / `UiEffect`, fixes Main-thread blocking I/O in `PdfViewer.kt`, and establishes a step-by-step verification protocol to ensure clean builds with zero test regressions.

---

## 1. Observation

### 1.1 Existing Test Suite Inventory
The test suite in `app/src/test/java/com/example/` consists of 14 test files across unit, integration, UI, screenshot, and performance domains:

| File Path | Test Count | Current Status | Primary Scope |
|---|---|---|---|
| `app/src/test/java/com/example/MainViewModelTest.kt` | 8 | PASS | ViewModel state flows, password CRUD, PDF filter, picker trigger |
| `app/src/test/java/com/example/MainActivityTest.kt` | 8 | PASS | `getFileName` URI parsing, Intent dispatch to ViewModel |
| `app/src/test/java/com/example/ui/ComposeUiTests.kt` | 5 | PASS | Composable rendering, dialog interactions, Activity launch |
| `app/src/test/java/com/example/PDFDecryptorScreenshotTest.kt` | 4 | PASS | Roborazzi screenshot visual regression (empty, batch, success, light) |
| `app/src/test/java/com/example/MultiDeviceScreenshotTest.kt` | 5 | PASS | Roborazzi multi-device layout (Pixel 8, 4a, Fold, Tablet) |
| `app/src/test/java/com/example/DecryptPdfTest.kt` | 3 | PASS | PDFBox decryption logic and status returns |
| `app/src/test/java/com/example/RealEncryptedPdfIntegrationTest.kt` | 5 | PASS | Dynamic real PDF generation, AES encryption, AutoUnlock |
| `app/src/test/java/com/example/domain/usecase/DomainUseCasesTest.kt` | 2 | PASS | UseCase unit isolation with `FakePasswordDao` |
| `app/src/test/java/com/example/PasswordRepositoryTest.kt` | 2 | PASS | Room DB + Keystore CryptoManager CRUD |
| `app/src/test/java/com/example/FileUtilsTest.kt` | 3 | PASS | URI resolution and DoD 3-pass file shredding |
| `app/src/test/java/com/example/ThemePreferencesTest.kt` | 1 | PASS | DataStore theme mode persistence |
| `app/src/test/java/com/example/CryptoManagerTest.kt` | 2 | PASS | AES-GCM-256 encryption/decryption and plaintext fallback |
| `app/src/test/java/com/example/PerformanceTest.kt` | 1 | PASS | Sync vs Async URI file name resolution benchmarks |
| `app/src/test/java/com/example/ExampleUnitTest.kt` | 1 | PASS | Basic unit sanity test |

*Baseline verification executed on Windows Host:*
`$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:testDebugUnitTest` → **33 tasks executed / UP-TO-DATE, BUILD SUCCESSFUL**.

---

### 1.2 Identified Test Suite Breakages under Milestone 1 Refactoring

#### A. `MainViewModelTest.kt`
- **Current Observation (Lines 66-72, 78-79, 118-121, 209-215)**:
  Directly asserts against individual `MutableStateFlow` properties that will be encapsulated into `MainUiState` or `UiEffect`:
  ```kotlin
  // Line 67-71 (MainViewModelTest.kt)
  assertEquals(ConflictMode.SAVE_AS_COPY, viewModel.conflictMode.value)
  assertFalse(viewModel.rememberConflictChoice.value)
  assertTrue(viewModel.selectedUris.value.isEmpty())
  assertFalse(viewModel.isProcessing.value)
  assertNull(viewModel.statusMessage.value)

  // Line 210-214 (MainViewModelTest.kt)
  assertFalse(viewModel.requestOpenDocumentPicker.value)
  viewModel.triggerOpenDocumentPicker()
  assertTrue(viewModel.requestOpenDocumentPicker.value)
  ```
- **Impact**: Removing or making private the standalone `MutableStateFlow`s (`selectedUris`, `selectedFileNames`, `conflictMode`, `rememberConflictChoice`, `requestOpenDocumentPicker`, etc.) will fail compilation of `MainViewModelTest.kt`.

#### B. `MainActivityTest.kt`
- **Current Observation (Lines 96-102, 105-116)**:
  Directly accesses `viewModel.showPasswordListDialog.value` and `viewModel.requestOpenDocumentPicker.value`:
  ```kotlin
  // Line 101 (MainActivityTest.kt)
  org.junit.Assert.assertTrue(viewModel.showPasswordListDialog.value)

  // Line 111 (MainActivityTest.kt)
  org.junit.Assert.assertTrue(viewModel.requestOpenDocumentPicker.value)
  ```
- **Impact**: These boolean flows will be eliminated in favor of `MainUiState` flags or `UiEffect` one-off events (`UiEffect.LaunchFilePicker`, `MainUiState.showPasswordListDialog`).

#### C. `ui/ComposeUiTests.kt`
- **Current Observation (Lines 10-13)**:
  Imports composable functions from the root package `com.example`:
  ```kotlin
  import com.example.PasswordInputSection
  import com.example.SavePasswordDialog
  import com.example.SelectedFilesCard
  ```
- **Impact**: In M1, these composables are moved into modular package directories:
  - `PasswordInputSection` → `com.example.ui.components.PasswordInputSection`
  - `SavePasswordDialog` → `com.example.feature.vault.SavePasswordDialog`
  - `SelectedFilesCard` → `com.example.ui.components.SelectedFilesCard`
  - `PdfViewerScreen` → `com.example.feature.viewer.PdfViewerScreen`

#### D. `PDFDecryptorScreenshotTest.kt` & `MultiDeviceScreenshotTest.kt`
- **Current Observation (Lines 34, 46-66, 82-86 in `PDFDecryptorScreenshotTest.kt`)**:
  1. Imports `PDFDecryptorScreen` from root `com.example` (will move to `com.example.feature.decrypt.PDFDecryptorScreen`).
  2. Sets up UI states by directly assigning to mutable fields on `MainViewModel`:
     ```kotlin
     viewModel.selectedUris.value = listOf(...)
     viewModel.selectedFileNames.value = listOf(...)
     viewModel.selectedMetadata.value = PdfMetadata(...)
     viewModel.password.value = "secret123"
     viewModel.lastDecryptedUri.value = sampleUri
     viewModel.statusMessage.value = "✅ Decrypted & Saved (1 file)"
     ```
- **Impact**: Fails compilation when individual mutable properties are unified into `StateFlow<MainUiState>`.

---

### 1.3 Threading Violation in `PdfViewer.kt`
- **Current Observation (`app/src/main/java/com/example/ui/PdfViewer.kt`, Lines 103-167)**:
  ```kotlin
  DisposableEffect(uri) {
      var tempFile: java.io.File? = null
      try {
          val fileDescriptor: ParcelFileDescriptor? = try {
              context.contentResolver.openFileDescriptor(uri, "r") // <-- MAIN THREAD CONTENT RESOLVER I/O
          } catch (_: Exception) {
              null
          } ?: run {
              val temp = java.io.File(context.cacheDir, "preview_temp_${System.currentTimeMillis()}.pdf")
              val inputStream = try {
                  context.contentResolver.openInputStream(uri) // <-- MAIN THREAD STREAM I/O
              } catch (_: Exception) { ... }

              inputStream?.use { input ->
                  temp.outputStream().use { output -> input.copyTo(output) } // <-- MAIN THREAD DISK WRITE
              }
              tempFile = temp
              if (temp.exists() && temp.length() > 0) {
                  ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
              } else null
          }

          if (fileDescriptor != null) {
              pfd = fileDescriptor
              val pdfRenderer = PdfRenderer(fileDescriptor) // <-- MAIN THREAD NATIVE PDF INITIALIZATION
              renderer = pdfRenderer
              pageCount = pdfRenderer.pageCount
              isLoading = false
          } ...
      }
      onDispose {
          ...
          com.example.util.FileUtils.secureDelete(tempFile) // <-- MAIN THREAD 3-PASS FILE SHREDDING DISK I/O
          ...
      }
  }
  ```
- **Direct Violation**:
  1. `context.contentResolver.openFileDescriptor(uri, "r")` executes synchronously inside Compose composition on the Android Main thread.
  2. Fallback stream read/write (`input.copyTo(output)`) writes entire multi-megabyte PDF files to disk on the Main thread.
  3. Native `PdfRenderer` header parsing runs on the Main thread.
  4. `FileUtils.secureDelete(tempFile)` executes DoD 5220.22-M 3-pass byte overwriting (0x00, 0xFF, random + fsync) synchronously on the Main thread during `onDispose`.
  5. Violates AGENTS.md Section 4: *"Zero I/O on Main Thread: ContentResolver queries, SAF operations, and Apache PDFBox parsing MUST run on Dispatchers.IO."*

---

## 2. Logic Chain

### 2.1 State Migration: Multiple StateFlows → `MainUiState`
1. Moving from disparate `MutableStateFlow`s to `MainUiState` creates a single source of truth and enforces unidirectional data flow (UDF).
2. For testing, `viewModel.uiState.value.<field>` allows reading any snapshot state without race conditions.
3. For asynchronous state transitions, `app.cash.turbine:turbine` (version 1.2.0, already present in `libs.versions.toml`) provides deterministic event stream assertion:
   ```kotlin
   viewModel.uiState.test {
       val initialState = awaitItem()
       assertEquals(ConflictMode.SAVE_AS_COPY, initialState.conflictMode)
       
       viewModel.onAction(MainUiAction.UpdateConflictSettings(ConflictMode.OVERWRITE, true))
       val updatedState = awaitItem()
       assertEquals(ConflictMode.OVERWRITE, updatedState.conflictMode)
       assertTrue(updatedState.rememberConflictChoice)
   }
   ```

### 2.2 Event Stream Migration: Boolean Flows → `UiEffect`
1. Single-shot operations (launching file picker, triggering biometric auth, showing snackbars) should not be stored as persistent boolean flags in UI state.
2. In `MainViewModel`, replace `requestOpenDocumentPicker = MutableStateFlow(false)` with a channel-backed `uiEffect: Flow<UiEffect>`.
3. In `MainViewModelTest.kt`, test the effect emission using Turbine:
   ```kotlin
   viewModel.uiEffect.test {
       viewModel.onAction(MainUiAction.OpenFilePicker)
       assertEquals(UiEffect.LaunchFilePicker, awaitItem())
   }
   ```
4. In `MainActivityTest.kt`, `MainActivity` consumes `viewModel.uiEffect` in a `LaunchedEffect(Unit)` or `repeatOnLifecycle` block. Intent handling (e.g. `ACTION_SELECT_PDF`) routes to `viewModel.onAction(MainUiAction.OpenFilePicker)` which emits the effect and triggers `activity.startActivityForResult(ACTION_OPEN_DOCUMENT)`. The test continues to assert on `shadowOf(activity).nextStartedActivityForResult`.

### 2.3 Screenshot Test Decoupling via Stateless Composables
1. Following Google MAD & AGENTS.md standards (Feature 5: Composable Leaf Decoupling):
   - `PDFDecryptorScreen` will have a stateless overload:
     ```kotlin
     @Composable
     fun PDFDecryptorScreen(
         uiState: MainUiState,
         onAction: (MainUiAction) -> Unit,
         modifier: Modifier = Modifier,
         snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
         windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
     )
     ```
   - And a stateful root wrapper:
     ```kotlin
     @Composable
     fun PDFDecryptorScreen(
         viewModel: MainViewModel,
         modifier: Modifier = Modifier,
         snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
         windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
     ) {
         val uiState by viewModel.uiState.collectAsStateWithLifecycle()
         PDFDecryptorScreen(
             uiState = uiState,
             onAction = viewModel::onAction,
             modifier = modifier,
             snackbarHostState = snackbarHostState,
             windowWidthSizeClass = windowWidthSizeClass
         )
     }
     ```
2. In `PDFDecryptorScreenshotTest.kt`, screenshot tests can directly render the stateless `PDFDecryptorScreen(uiState = MainUiState(...), onAction = {})`:
   - Eliminates the need to instantiate full `MainViewModel` with mock databases/dispatchers for static visual tests.
   - Eliminates coroutine race conditions and timing flakes.
   - Provides 100% deterministic screenshot capture.

### 2.4 Fixing `PdfViewer.kt` Threading Isolation
1. Replace Main-thread `DisposableEffect(uri)` with `LaunchedEffect(uri)` executing on `Dispatchers.IO`:
   ```kotlin
   LaunchedEffect(uri) {
       isLoading = true
       errorMessage = null
       withContext(Dispatchers.IO) {
           try {
               val descriptor = try {
                   context.contentResolver.openFileDescriptor(uri, "r")
               } catch (_: Exception) { null } ?: run {
                   val temp = File(context.cacheDir, "preview_temp_${System.currentTimeMillis()}.pdf")
                   val inputStream = try {
                       context.contentResolver.openInputStream(uri)
                   } catch (_: Exception) {
                       if (uri.scheme == "file" && uri.path != null) FileInputStream(File(uri.path!!)) else null
                   }
                   inputStream?.use { input ->
                       temp.outputStream().use { output -> input.copyTo(output) }
                   }
                   tempFile = temp
                   if (temp.exists() && temp.length() > 0) {
                       ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                   } else null
               }

               if (descriptor != null) {
                   pfd = descriptor
                   val pdfRenderer = PdfRenderer(descriptor)
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
       }
   }
   ```
2. For cleanup on disposal:
   ```kotlin
   DisposableEffect(uri) {
       onDispose {
           try { renderer?.close() } catch (_: Exception) {}
           try { pfd?.close() } catch (_: Exception) {}
           tempFile?.let { file ->
               // Execute file shredding off the UI thread
               kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                   FileUtils.secureDelete(file)
               }
           }
           val snapshot = pageBitmapCache.snapshot()
           pageBitmapCache.evictAll()
           snapshot.values.forEach { bmp ->
               if (bmp != null && !bmp.isRecycled) {
                   bmp.recycle()
               }
           }
       }
   }
   ```
3. Thread safety in `PdfPageItem`:
   `PdfRenderer` is not thread-safe. All page open/render calls inside `LaunchedEffect(pageIndex)` in `PdfPageItem` must continue to be synchronized:
   `withContext(Dispatchers.IO) { synchronized(renderer) { ... } }`.

---

## 3. Caveats
1. **Windows Environment**: All command executions must explicitly supply `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` when running Gradle from PowerShell.
2. **Roborazzi Screenshot Golden Hashes**: Reorganizing composables into modular files does not alter the UI layout tree, so existing Roborazzi screenshot goldens in `app/src/test/screenshots/` will remain valid. If minor margin or styling adjustments are introduced, run `./gradlew.bat :app:recordRoborazziDebug` to regenerate baseline goldens.
3. **Action Function Signatures**: During M1 implementation, keep convenience forwarding functions on `MainViewModel` (e.g. `savePassword(...)`, `deletePassword(...)`, `updateConflictSettings(...)`) alongside `onAction(action: MainUiAction)` to maintain dual compatibility where convenient while migrating tests.

---

## 4. Conclusion & Test Migration Plan

### 4.1 Required Test Modifications Blueprint

#### 1. `MainViewModelTest.kt` Updates:
```kotlin
@Test
fun testInitialState() {
    val state = viewModel.uiState.value
    assertEquals(ConflictMode.SAVE_AS_COPY, state.conflictMode)
    assertFalse(state.rememberConflictChoice)
    assertTrue(state.selectedUris.isEmpty())
    assertFalse(state.isProcessing)
    assertNull(state.statusMessage)
}

@Test
fun testUpdateConflictSettings() = runTest {
    viewModel.updateConflictSettings(ConflictMode.OVERWRITE, true)
    assertEquals(ConflictMode.OVERWRITE, viewModel.uiState.value.conflictMode)
    assertTrue(viewModel.uiState.value.rememberConflictChoice)

    val newViewModel = MainViewModel(application, repository, ioDispatcher = testDispatcher)
    assertEquals(ConflictMode.OVERWRITE, newViewModel.uiState.value.conflictMode)
    assertTrue(newViewModel.uiState.value.rememberConflictChoice)

    viewModel.updateConflictSettings(ConflictMode.SAVE_AS_COPY, false)
    assertFalse(viewModel.uiState.value.rememberConflictChoice)
}

@Test
fun testSetSelectedUris_filtersPdfOnly() = runTest {
    val pdfUri = Uri.parse("file:///storage/emulated/0/Download/document.pdf")
    val txtUri = Uri.parse("file:///storage/emulated/0/Download/document.txt")

    viewModel.setSelectedUris(application, listOf(pdfUri, txtUri))
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.selectedUris.size)
    assertEquals(pdfUri, viewModel.uiState.value.selectedUris[0])
    assertEquals(1, viewModel.uiState.value.selectedFileNames.size)
    assertEquals("document.pdf", viewModel.uiState.value.selectedFileNames[0])
}

@Test
fun testTriggerOpenDocumentPicker_emitsUiEffect() = runTest {
    viewModel.uiEffect.test {
        viewModel.triggerOpenDocumentPicker()
        assertEquals(UiEffect.LaunchFilePicker, awaitItem())
    }
}
```

#### 2. `MainActivityTest.kt` Updates:
```kotlin
// Update imports
import com.example.util.FileUtils.getFileName

@Test
fun handleIntent_showSavedPasswords_opensPasswordDialog() {
    val intent = android.content.Intent("com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS")
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    val activity = controller.get()
    val viewModel: MainViewModel by activity.viewModels()
    org.junit.Assert.assertTrue(viewModel.uiState.value.showPasswordListDialog)
}

@Test
fun handleIntent_selectPdf_triggersDocumentPicker() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    val intent = android.content.Intent("com.max97k.pddf.ACTION_SELECT_PDF")
    controller.newIntent(intent)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    val startedIntent = shadowOf(activity).nextStartedActivityForResult
    org.junit.Assert.assertNotNull(startedIntent)
    org.junit.Assert.assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, startedIntent.intent.action)
}
```

#### 3. `ui/ComposeUiTests.kt` Updates:
```kotlin
// Update package imports
import com.example.ui.components.PasswordInputSection
import com.example.feature.vault.SavePasswordDialog
import com.example.ui.components.SelectedFilesCard
import com.example.feature.viewer.PdfViewerScreen
```

#### 4. `PDFDecryptorScreenshotTest.kt` Updates:
```kotlin
import com.example.feature.decrypt.PDFDecryptorScreen
import com.example.MainUiState

@Test
fun screenshot_2_files_selected_batch() {
    val state = MainUiState(
        selectedUris = listOf(
            Uri.parse("content://com.example.provider/Bank_Statement_July.pdf"),
            Uri.parse("content://com.example.provider/Tax_Return_2023.pdf"),
            Uri.parse("content://com.example.provider/Paystub_Oct.pdf")
        ),
        selectedFileNames = listOf(
            "Bank_Statement_July.pdf",
            "Tax_Return_2023.pdf",
            "Paystub_Oct.pdf"
        ),
        selectedMetadata = PdfMetadata(
            title = "Bank Statement July",
            author = "Financial Services Corp",
            pageCount = 6,
            fileSizeMb = 2.45,
            encryptionMethod = "AES 256-bit",
            canPrint = true,
            canCopy = false
        ),
        password = "secret123"
    )

    composeTestRule.setContent {
        MyApplicationTheme(themeMode = ThemeMode.DARK) {
            PDFDecryptorScreen(uiState = state, onAction = {})
        }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/real_ss_2_batch_selection.png")
}
```

#### 5. `MultiDeviceScreenshotTest.kt` Updates:
```kotlin
import com.example.feature.decrypt.PDFDecryptorScreen
```

---

## 5. Verification Method

### 5.1 Verification Commands (PowerShell / Windows)

```powershell
# Set JAVA_HOME
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 1. Verify ViewModel unit tests with Turbine
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.MainViewModelTest"

# 2. Verify Activity intent handling & URI parsing
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.MainActivityTest"

# 3. Verify modularized Compose UI components
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.ui.ComposeUiTests"

# 4. Verify visual regression screenshot suites
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.PDFDecryptorScreenshotTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.MultiDeviceScreenshotTest"

# 5. Full test suite execution & verification
.\gradlew.bat :app:testDebugUnitTest

# 6. Assembly check
.\gradlew.bat :app:assembleDebug
```

### 5.2 Verification Checklist for Implementers
- [ ] `MainUiState` is single immutable data class exposed as `StateFlow<MainUiState>`.
- [ ] `UiEffect` emits single-shot events over a Kotlin Coroutines `Channel` / `Flow`.
- [ ] No ContentResolver operations or PDF parsing in `DisposableEffect` / Compose composition branches.
- [ ] `PdfViewerScreen` initializes `PdfRenderer` and loads files exclusively on `Dispatchers.IO`.
- [ ] Leaf composables in `ui/components/` and `feature/` accept only state and lambdas.
- [ ] All 14 test suites pass cleanly with 0 compilation errors and 0 test failures.
