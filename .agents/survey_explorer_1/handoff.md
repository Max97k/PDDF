# Survey Explorer 1 — Codebase & Architecture Surveyor Handoff Report

## 1. Observation

### 1.1 Project Structure & Build Configuration
- **Root Gradle Files**:
  - `build.gradle.kts` configures top-level plugins: Android Application (`libs.plugins.android.application`), Compose (`libs.plugins.kotlin.compose`), KSP (`libs.plugins.google.devtools.ksp`), Roborazzi (`libs.plugins.roborazzi`), Secrets (`libs.plugins.secrets`), Google Services (`libs.plugins.google.services`).
  - `settings.gradle.kts` uses Foojay toolchain resolver convention `1.0.0`, root project name `"PDF Decryptor"`, and includes module `:app`.
  - `gradle.properties` sets `org.gradle.jvmargs=-Xmx4g`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `kotlin.compiler.execution.strategy=in-process`, `android.enableR8.fullMode=true`.
  - `gradle/libs.versions.toml`:
    - Android Gradle Plugin (AGP): `9.1.1`
    - Kotlin: `2.2.10` (Compose Compiler plugin via Kotlin 2.0+)
    - Target SDK: `35`, Compile SDK: `35`, Min SDK: `24`
    - Compose BOM: `2024.09.00`
    - KSP: `2.3.5`
    - AndroidX Core KTX: `1.15.0`
    - Lifecycle & ViewModel Compose: `2.8.7`
    - Activity Compose: `1.10.1`
    - Room: `2.7.0` (with `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler` via KSP)
    - Kotlinx Coroutines: `1.10.2`
    - Robolectric: `4.16.1`
    - Roborazzi: `1.59.0` (with compose & junit-rule)
    - PDFBox Android: `2.0.27.0` (`com.tom-roush:pdfbox-android`)
    - Biometric: `1.1.0` (`androidx.biometric:biometric`)
    - DataStore Preferences: `1.1.1`
    - AndroidX Startup: `1.2.0`
    - Profile Installer: `1.4.1`
    - DocumentFile: `1.0.1`
  - `app/build.gradle.kts`:
    - `applicationId = "com.max97k.pddf"`, `versionCode = 7`, `versionName = "0.3.0"`.
    - Locale filters: `localeFilters += listOf("zh-rTW", "en")`.
    - JaCoCo test report task configured (`jacocoTestReport`).
    - NDK `debugSymbolLevel = "FULL"` in release build.
    - Proguard rules: `isMinifyEnabled = true`, `isShrinkResources = true`, `enableR8.fullMode = true`.

### 1.2 Monolithic Components & Existing Architecture
- **`app/src/main/java/com/example/MainActivity.kt` (1245 lines)**:
  - Acts as a monolithic god-activity / composable container combining:
    1. Activity lifecycle (`onCreate`, `onPause`, `onResume`, `onNewIntent`).
    2. Intent dispatching (`ACTION_VIEW`, `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_SHOW_SAVED_PASSWORDS`, `ACTION_SELECT_PDF`).
    3. Direct BiometricPrompt instantiations in composables (lines 480-524).
    4. Window `FLAG_SECURE` toggling via `DisposableEffect` (lines 209-218).
    5. 4 separate `rememberLauncherForActivityResult` contracts (`openDocumentLauncher`, `openDocumentTreeLauncher`, `saveDecryptedPdfLauncher`, `createDocumentLauncher`).
    6. Drag-and-drop target integration (`DragAndDropTarget`, lines 297-331).
    7. Embedded Composables:
       - `PDFDecryptorScreen` (lines 158-954)
       - `SelectedFilesCard` (lines 956-996)
       - `PasswordInputSection` (lines 998-1057)
       - `SavePasswordDialog` (lines 1059-1095)
       - `SavedPasswordListDialog` (lines 1097-1201)
       - `DocumentDetailsCard` (lines 1207-1244)
       - `getFileName` standalone top-level function (lines 1203-1205).
    8. Embedded modal dialogs:
       - Batch progress modal dialog with cancel button (lines 774-803)
       - What's New version changelog dialog (lines 805-816)
       - Auto-unlocking spinner dialog (lines 818-842)
       - Manual auto-unlock password input prompt dialog with "Remember password" checkbox (lines 844-923)
       - `PdfViewerDialog` popup (lines 925-953).

- **`app/src/main/java/com/example/MainViewModel.kt` (551 lines)**:
  - Manages state via 20+ disconnected `MutableStateFlow` properties:
    - `themeMode: StateFlow<ThemeMode>`
    - `savedPasswords: StateFlow<List<PasswordEntity>>`
    - `selectedUris = MutableStateFlow<List<Uri>>(emptyList())`
    - `selectedFileNames = MutableStateFlow<List<String>>(emptyList())`
    - `selectedMetadata = MutableStateFlow<PdfMetadata?>(null)`
    - `isProcessing = MutableStateFlow(false)`
    - `statusMessage = MutableStateFlow<String?>(null)`
    - `lastDecryptedUri = MutableStateFlow<Uri?>(null)`
    - `conflictMode = MutableStateFlow(ConflictMode.SAVE_AS_COPY)`
    - `rememberConflictChoice = MutableStateFlow(false)`
    - `password = MutableStateFlow("")`
    - `passwordVisible = MutableStateFlow(false)`
    - `showSavePasswordDialog = MutableStateFlow(false)`
    - `showPasswordListDialog = MutableStateFlow(false)`
    - `requestOpenDocumentPicker = MutableStateFlow(false)`
    - `isAutoUnlocking = MutableStateFlow(false)`
    - `showAutoUnlockPasswordPrompt = MutableStateFlow(false)`
    - `autoUnlockTargetUri = MutableStateFlow<Uri?>(null)`
    - `autoUnlockFileName = MutableStateFlow("")`
    - `autoUnlockErrorMessage = MutableStateFlow<String?>(null)`
    - `previewPdfUri = MutableStateFlow<Uri?>(null)`
    - `batchState = MutableStateFlow(BatchState())`
  - Incomplete/Divergent state object:
    - `val pdfUiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)` exists at line 122 but is completely disconnected from the rest of the ViewModel and Composable tree.
  - Lacks a single-shot event stream (`UiEffect`) for handling transient events (e.g. snackbar messages, biometric prompts, activity result launches, haptics).
  - Holds default instantiation of Room database, UseCases, and `SharedPreferences` directly in default constructor parameters rather than clean factory injection.

### 1.3 Existing Core Functionalities
1. **PDF Auto-Unlock**:
   - Implemented via `AutoUnlockUseCase` (`app/src/main/java/com/example/domain/usecase/AutoUnlockUseCase.kt`).
   - Triggered when receiving a single PDF via `ACTION_VIEW` or `ACTION_SEND`.
   - Checks encryption status via PDFBox `PDDocument.load(...)`.
   - If encrypted, sequentially iterates through saved passwords decrypted from `PasswordVaultUseCase` / `PasswordRepository`.
   - If matching password found, decrypts to temporary cache file and opens preview.
   - If not found or wrong password, prompts user via `showAutoUnlockPasswordPrompt`.
2. **Batch Decryption**:
   - Implemented via `BatchProcessUseCase` (`app/src/main/java/com/example/domain/usecase/BatchProcessUseCase.kt`).
   - Supports in-place overwrite (`processInPlace`) and output directory export (`processToDirectory`) using AndroidX `DocumentFile`.
   - Handles conflict resolution modes: `ConflictMode.OVERWRITE` and `ConflictMode.SAVE_AS_COPY` (with `getUniqueFileName` automatic numbered increment `(1).pdf`).
   - Supports coroutine cancellation (`cancelBatch()` via `Job.cancel()`).
3. **Password Vault**:
   - Backed by Room database `AppDatabase` (version 2, table `passwords`).
   - Hardware Keystore DB encryption via `CryptoManager` (`app/src/main/java/com/example/util/CryptoManager.kt`) using AES-128 GCM (`AndroidKeyStore`), prefixing encrypted values with `ENC_`.
   - Migration `MIGRATION_1_2` safely handles legacy plaintext entries.
   - Biometric authentication (`BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) guards opening the vault list.
   - In-memory auto-timeout clears sensitive password fields after 60 seconds in background (`onAppBackgrounded` / `onAppForegrounded`).
   - `FLAG_SECURE` prevents screenshot capture when password dialogs are shown.
   - 5-second Snackbar UNDO mechanism on deletion (`restorePassword`).
4. **Launcher Shortcuts & Quick Settings Tile**:
   - Declared in `app/src/main/res/xml/shortcuts.xml` with shortcut IDs `shortcut_select_pdf` (`com.max97k.pddf.ACTION_SELECT_PDF`) and `shortcut_saved_passwords` (`com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS`).
   - `PdfDecryptorTileService` extends `TileService` to bind to `android.service.quicksettings.action.QS_TILE` and launch `MainActivity`.
5. **In-App Native PDF Viewer**:
   - `app/src/main/java/com/example/ui/PdfViewer.kt`: Uses native `android.graphics.pdf.PdfRenderer` with an LRU bitmap memory cache (12.5% of max heap), pinch-to-zoom / pan gestures, floating page indicator, and fallback file streaming.

### 1.4 Git History, Jules Patches, and Working Tree State
- **Git Commit History**:
  - Full history reflects 47+ progressive modernization commits (from initial PR merges `627f13f`, `03441e0`, `0d1d004`, `77a725d`, `024eefe`, `bbfe3c2`, `bef8f54` through optimization stages 1-5, release v0.2.0, v0.2.1, v0.2.2, to v0.3.0 Clean Architecture UseCases at `c86a925`).
  - Current `HEAD` is `8b1cc9e docs: add Google Play testing links and badges to READMEs`.
- **Working Tree Changes (Uncommitted)**:
  - Working tree currently contains modifications in 6 files:
    - `app/src/main/AndroidManifest.xml` (added custom intent filter actions `com.max97k.pddf.ACTION_SHOW_SAVED_PASSWORDS` and `com.max97k.pddf.ACTION_SELECT_PDF`)
    - `app/src/main/java/com/example/MainActivity.kt` (handled shortcut actions and launched document picker)
    - `app/src/main/java/com/example/MainViewModel.kt` (added `requestOpenDocumentPicker` StateFlow)
    - `app/src/main/res/xml/shortcuts.xml` (standardized package and action strings to `com.max97k.pddf`)
    - `app/src/test/java/com/example/MainActivityTest.kt` (added tests `handleIntent_showSavedPasswords_opensPasswordDialog` and `handleIntent_selectPdf_triggersDocumentPicker`)
    - `app/src/test/java/com/example/MainViewModelTest.kt` (added `testTriggerOpenDocumentPicker`).
- **`.Jules/` Directory**:
  - Contains `bolt.md` (recording offloading I/O ContentResolver queries to `Dispatchers.IO`) and `palette.md` (recording accessibility guidelines for redundant/contextual/dynamic content descriptions).

---

## 2. Logic Chain

1. **Monolithic Activity & ViewModel Deconstruction (Requirement R1)**:
   - *Observation*: `MainActivity.kt` contains 1245 lines with 6 full composable components and 5 dialogs inline. `MainViewModel.kt` has 20+ disconnected `MutableStateFlow`s and lacks a single UDF pattern or `UiEffect` event bus.
   - *Reasoning*:
     - To adhere to Clean Architecture and MAD (Modern Android Development) guidelines in `AGENTS.md`, the monolithic activity must be modularized into discrete packages:
       - `ui/components/`: Shared UI components (`SelectedFilesCard.kt`, `PasswordInputSection.kt`, `DocumentDetailsCard.kt`, `EmptyStateCard.kt`, `ThemeDropdownMenu.kt`).
       - `feature/vault/`: Vault UI (`SavedPasswordListDialog.kt`, `SavePasswordDialog.kt`, `BiometricPromptHelper.kt`).
       - `feature/viewer/`: Native viewer dialog & screen (`PdfViewerDialog.kt`, `PdfViewerScreen.kt`, `PdfPageItem.kt`).
       - `feature/decrypt/`: Main decrypt screen composable (`PDFDecryptorScreen.kt`), batch progress dialog (`BatchProgressDialog.kt`), auto-unlock prompt dialog (`AutoUnlockPasswordDialog.kt`).
     - `MainUiState` must be created as a single immutable `data class` exposing the entire state of the screen (`selectedFiles`, `password`, `isProcessing`, `batchState`, `dialogState`, `themeMode`, `statusMessage`), collected via `collectAsStateWithLifecycle()`.
     - `UiEffect` sealed interface must be introduced to handle one-off side effects via a Kotlin Coroutine `Channel<UiEffect>`:
       - `ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)`
       - `LaunchFilePicker(val allowMultiple: Boolean)`
       - `LaunchDirectoryPicker`
       - `LaunchSavePicker(val defaultFileName: String)`
       - `TriggerBiometricPrompt`
       - `PerformHaptic(val type: HapticFeedbackType)`
       - `OpenPdfIntent(val uri: Uri)`
       - `SharePdfIntent(val uri: Uri)`

2. **Hardware Security & Memory Protections (Requirement R2)**:
   - *Observation*: `CryptoManager.kt` uses AES/GCM/NoPadding with `AndroidKeyStore`. `FileUtils.kt` has `secureDelete` (DoD random overwrite).
   - *Reasoning*:
     - Next phases can extend this with StrongBox Keymaster detection (`KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)` with fallback), memory zeroization (`CharArray` wiping for passwords), and WorkManager background worker integration (`BatchDecryptWorker`) for ongoing foreground notifications.

3. **Android 15 & Adaptive Form Factors (Requirement R3)**:
   - *Observation*: App targets SDK 35 (`compileSdk = 35`, `targetSdk = 35`), utilizes `WindowWidthSizeClass`, and has `enableOnBackInvokedCallback="true"` in AndroidManifest.
   - *Reasoning*:
     - Predictive back transitions and IME insets padding (`WindowInsets.ime`, `WindowInsets.safeDrawing`) are already partially integrated; full tablet dual-pane and foldable tabletop awareness can leverage WindowSizeClass and Jetpack WindowManager.

4. **Testing & Verification**:
   - *Observation*: Running `.\gradlew.bat :app:testDebugUnitTest` with `JAVA_HOME` pointing to Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`) succeeds in 6s with 33 actionable tasks and 0 errors across Robolectric, use-case unit tests, integration tests, and Compose UI tests.
   - *Reasoning*: The test harness is healthy and deterministic, providing a strong safety net for the upcoming refactoring.

---

## 3. Caveats

1. **Java Environment Path**:
   - The default PowerShell PATH does not include `java.exe`. Any command running `gradlew.bat` MUST set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` in the same command block or terminal session.
2. **Robolectric Configuration**:
   - Robolectric tests specify `@Config(sdk = [34])` due to Robolectric 4.16.1 shadow environment compatibility with Android 15 (API 35).
3. **Database Versioning**:
   - `AppDatabase` is at version 2. Any modifications to entities must provide explicit Room migrations (`MIGRATION_2_3`) to prevent destructive data loss of user passwords.

---

## 4. Conclusion

The PDDF codebase is in a stable, functional, and fully testable state at `v0.3.0` (commit `8b1cc9e`). The core capabilities (PDF auto-unlock, batch decryption with overwrite/copy modes, encrypted password vault with biometric unlock and auto-timeout, static shortcuts, QS tile, and native in-app PDF viewer) are verified and working.

However, `MainActivity.kt` (1245 lines) and `MainViewModel.kt` (551 lines with 20+ fragmented StateFlows) represent classic monolithic bottlenecks.

### Target Architecture Package Blueprint for R1 Refactoring:
```
app/src/main/java/com/example/
├── MainActivity.kt                      # Slim Activity (~100 lines): Lifecycle, Intent dispatch, System Insets, UI Scaffold
├── MainViewModel.kt                     # UDF ViewModel: StateFlow<MainUiState>, Channel<UiEffect>, clean UseCase orchestration
├── MainUiState.kt                       # Single immutable UI state data class
├── UiEffect.kt                          # Single-shot UI effects sealed interface
├── feature/
│   ├── decrypt/
│   │   ├── PDFDecryptorScreen.kt        # Primary decrypt screen composable
│   │   ├── BatchProgressDialog.kt       # Batch decryption progress dialog
│   │   └── AutoUnlockPasswordDialog.kt  # Auto-unlock password prompt dialog
│   ├── vault/
│   │   ├── SavedPasswordListDialog.kt   # Password vault list & search dialog
│   │   ├── SavePasswordDialog.kt        # Save password dialog
│   │   └── BiometricHelper.kt           # BiometricPrompt helper
│   └── viewer/
│       ├── PdfViewerDialog.kt           # Fullscreen PDF viewer dialog
│       ├── PdfViewerScreen.kt           # PDF viewer screen composable
│       └── PdfPageItem.kt               # Individual rendered page with LRU cache
├── ui/
│   ├── components/
│   │   ├── SelectedFilesCard.kt         # Selected files display & clear card
│   │   ├── PasswordInputSection.kt      # Password input field with action buttons
│   │   ├── DocumentDetailsCard.kt       # PDF metadata inspector card
│   │   ├── EmptyStateCard.kt            # Zero-state placeholder illustration & tips
│   │   ├── ThemeDropdownMenu.kt         # Theme selection dropdown menu
│   │   └── WhatsNewDialog.kt            # Changelog dialog
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── domain/
│   ├── model/ (PdfUiState, PdfMetadata, DecryptStatus, ConflictMode)
│   └── usecase/ (AutoUnlockUseCase, BatchProcessUseCase, DecryptPdfUseCase, PasswordVaultUseCase)
├── data/
│   ├── AppDatabase.kt, PasswordDao.kt, PasswordEntity.kt, PasswordRepository.kt, ThemePreferences.kt
├── initializer/
│   └── PdfBoxInitializer.kt
└── util/
    ├── CryptoManager.kt, FileUtils.kt, Result.kt
```

---

## 5. Verification Method

### Test Execution Command
Execute the test suite using PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest
```

### Verification Criteria
1. **Compilation**: Clean compilation with 0 warnings/errors.
2. **Unit Test Pass Rate**: 100% pass rate across:
   - `MainActivityTest` (file resolution & intent handling)
   - `MainViewModelTest` (UDF state transitions & conflict settings)
   - `DecryptPdfTest` (password validation & decryption)
   - `RealEncryptedPdfIntegrationTest` (real 128-bit encrypted PDF generation and decryption)
   - `DomainUseCasesTest` (Clean architecture use case boundary rules)
   - `ComposeUiTests` (UI interaction and dialog rendering)
   - `PasswordRepositoryTest` & `ThemePreferencesTest` (Data layer persistence)
   - `MultiDeviceScreenshotTest` & `PDFDecryptorScreenshotTest` (Roborazzi visual regression tests)
