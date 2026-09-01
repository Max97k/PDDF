# Specification & Testing Requirements Mining Report (Survey Spec Miner 3)

**Author**: Survey Spec Miner 3 (Specification & Testing Requirements Miner)  
**Date**: 2026-09-01T04:32:00Z  
**Scope**: R4 Requirements (Plurals, i18n, a11y, automated testing), 47 Jules Modernization Patches Inventory across R1-R4, and Build/Test Verification Harness.

---

## 1. Observation

Direct codebase inspection and test executions at `c:\Users\b\PDDF` revealed the following concrete findings:

### 1.1 Internationalization & Plurals Status
1. **No `<plurals>` definitions**: Running `grep_search` for `<plurals` in `app/src/main/res/values*` returned 0 results. Count strings in `app/src/main/res/values/strings.xml` (e.g. lines 5, 33, 35-39, 43) use hardcoded singular/plural patterns such as `"Selected %1$d file(s)"` and `"Decrypting %1$d file(s)..."`.
2. **Missing Simplified Chinese (`values-zh-rCN`)**:
   - `app/src/main/res/` contains: `values`, `values-zh-rTW`, `values-ja`, `values-es`, `values-de`, `values-fr`.
   - `values-zh-rCN/strings.xml` does **not exist** in the repository.
3. **Locale Filtering Restriction**:
   - `app/build.gradle.kts` line 30 explicitly restricts packaged resources:
     ```kotlin
     androidResources { localeFilters += listOf("zh-rTW", "en") }
     ```
     This strips out existing `ja`, `es`, `de`, `fr` resources during build.
   - `app/src/main/res/xml/locales_config.xml` lines 1–6 only lists `en` and `zh-TW`:
     ```xml
     <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
         <locale android:name="en"/>
         <locale android:name="zh-TW"/>
     </locale-config>
     ```
4. **Hardcoded User-Facing Strings in Composables**:
   - `MainActivity.kt` lines 398, 887, 1022, 1128, 1184, 1220, 1229, 1235–1240 contain hardcoded English strings (e.g. `"Theme Settings"`, `"Hide password"`, `"Show password"`, `"Search"`, `"No matching passwords"`, `"Delete ${savedPass.name}"`, `"Document Details"`, `"Title: "`, `"Author: "`, `"Pages: "`, `"File Size: "`, `"Encryption: "`, `"Permissions: "`).
   - `ui/PdfViewer.kt` line 386 uses hardcoded `"Page ${pageIndex + 1}"`.

### 1.2 Accessibility & TalkBack / WCAG 2.1 AA Status
1. **Semantics & Role Annotations**: No `Modifier.semantics` or Compose accessibility role/state properties are currently used in `app/src/main/java`.
2. **Touch Targets**: Standard `IconButton` components inherit 48dp touch targets, but custom clickable rows/cards (`DocumentDetailsCard` header, `SavedPasswordListDialog` item rows) do not explicitly declare `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` or `minimumInteractiveComponentSize()`.
3. **Dynamic Descriptions**: Toggles for password visibility and document details expansion use inline string literals rather than localized resource lookups.

### 1.3 Automated Test Suite & Harness Execution
1. **Execution Command**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --rerun-tasks
   ```
2. **Execution Results**: 33 Gradle tasks executed, **15 test suites**, **52 test cases passed, 0 failures, 0 errors, 0 skipped** (Build time: ~31s).
   - Test suites breakdown:
     - `CryptoManagerTest`: 2 tests passed
     - `DecryptPdfTest`: 3 tests passed
     - `DomainUseCasesTest`: 2 tests passed
     - `ExampleRobolectricTest`: 1 test passed
     - `ExampleUnitTest`: 1 test passed
     - `FileUtilsTest`: 3 tests passed
     - `MainActivityTest`: 8 tests passed
     - `MainViewModelTest`: 9 tests passed
     - `MultiDeviceScreenshotTest`: 5 tests passed (Pixel 8 light/dark, Pixel 4a, Pixel Fold, Pixel Tablet)
     - `PasswordRepositoryTest`: 2 tests passed
     - `PDFDecryptorScreenshotTest`: 4 tests passed
     - `PerformanceTest`: 1 test passed
     - `RealEncryptedPdfIntegrationTest`: 5 tests passed
     - `ThemePreferencesTest`: 1 test passed
     - `ComposeUiTests`: 5 tests passed
3. **Turbine Library**: `app.cash.turbine:turbine` is **not declared** in `gradle/libs.versions.toml` or `app/build.gradle.kts`. `MainViewModelTest.kt` currently uses `.first()` / polling coroutines.
4. **Clean Architecture Boundary Tests**: No architectural constraint enforcement tests exist to verify that the `domain` module/package has zero dependencies on `ui` or Android framework classes.
5. **JaCoCo Configuration**: `jacocoTestReport` task runs successfully but reports `"No class files specified"` because `classDirectories` points only to `${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug` rather than the updated AGP Kotlin/Java class outputs.

---

## 2. Logic Chain

1. **R4 Gap Analysis**:
   - **Plurals**: English and localized pluralization rules differ (e.g. Slavic/Arabic have complex rules; Chinese/Japanese have single `other` forms, English/Spanish have `one` and `other`). Replacing hardcoded strings with `<plurals>` and `pluralStringResource(...)` ensures grammatical correctness across all target languages.
   - **Locales**: To satisfy the R4 requirement for `zh-rTW`, `zh-rCN`, `ja`, and `es`, the project must:
     1. Create `values-zh-rCN/strings.xml` containing all 70+ translated strings and plurals.
     2. Update `values/strings.xml`, `values-zh-rTW/strings.xml`, `values-ja/strings.xml`, and `values-es/strings.xml` to include `<plurals>` and eliminate missing keys.
     3. Update `app/build.gradle.kts` `localeFilters` to `listOf("zh-rTW", "zh-rCN", "ja", "es", "en")`.
     4. Update `app/src/main/res/xml/locales_config.xml` to include `<locale android:name="zh-CN"/>`, `<locale android:name="ja"/>`, `<locale android:name="es"/>`.
     5. Extract all remaining hardcoded UI strings in `MainActivity.kt` and `ui/PdfViewer.kt` to `strings.xml`.
2. **Accessibility & WCAG 2.1 AA Compliance**:
   - TalkBack requires non-text UI elements to provide meaningful, localized labels.
   - Dynamic toggle states must update dynamically (e.g. `R.string.content_desc_show_password` vs `R.string.content_desc_hide_password`).
   - Contextual actions (e.g., deleting a specific password) must incorporate the item name dynamically using a localized template (`stringResource(R.string.content_desc_delete_specific, item.name)`).
   - Touch targets for all interactive composables must meet 48dp x 48dp minimum dimensions.
3. **Automated Testing & Test Harness Expansion**:
   - **Turbine**: Adding `app.cash.turbine:turbine` allows testing `StateFlow<MainUiState>` and `Channel<UiEffect>` emissions reactively without race conditions.
   - **Clean Architecture Boundary Tests**: Unit tests inspecting package imports/reflection or ArchUnit rules should be added to guarantee domain layer isolation.
   - **JaCoCo Classpath**: Update `classDirectories` in `jacocoTestReport` to include `intermediates/javac/debug/compileDebugJavaWithJavac/classes` and `tmp/kotlin-classes/debug` to generate accurate code coverage metrics.

---

## 3. Features Discovered (Full 47 Jules Modernization Patches Inventory)

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | R1: Architecture | Package Reorganization | Deconstruct monolithic `MainActivity.kt` into `ui/components/`, `feature/vault/`, `feature/viewer/`, `feature/decrypt/` | Source files | Modular packages | Compilation error on broken imports | ORIGINAL_REQUEST.md & Git commit `c86a925` |
| 2 | R1: Architecture | Unified MainUiState | Single immutable UI state data class replacing separate StateFlows | User & background events | `StateFlow<MainUiState>` | State updates flow through single reducer | ORIGINAL_REQUEST.md & `PdfUiState.kt` |
| 3 | R1: Architecture | UiEffect Single-Shot Channel | Single-shot channel/SharedFlow for one-off events (dialogs, picker, snackbar) | ViewModel trigger | `Flow<UiEffect>` | Events consumed exactly once | ORIGINAL_REQUEST.md & `MainViewModel.kt` |
| 4 | R1: Architecture | Clean Architecture UseCases | Isolated UseCases (`DecryptPdfUseCase`, `AutoUnlockUseCase`, `BatchProcessUseCase`, `PasswordVaultUseCase`) | Context, Uri, Passwords | Domain Result models | Explicit `Result.Error` or `DecryptStatus` | `com.example.domain.usecase.*` |
| 5 | R1: Architecture | Composable Leaf Decoupling | Leaf composables accept only state and lambdas; ViewModels remain at screen root | State data models, callbacks | UI emission (`Unit`) | N/A (compile-time type safety) | AGENTS.md § 3 & `ui/ComposeUiTests.kt` |
| 6 | R1: Architecture | Compose Stability & Immutability | `@Immutable` models and `ImmutableList` from `kotlinx.collections.immutable` | Data collections | Stable recomposition skips | N/A | Git commit `2add6e6` & `app/build.gradle.kts` |
| 7 | R1: Architecture | Explicit LazyColumn Keys & Content Types | Deterministic `key = { it.id }` and `contentType` in all lists | Item identifiers | Optimized list diffing | Prevents state loss during scrolling | Git commit `2add6e6` & `MainActivity.kt` |
| 8 | R1: Architecture | App Startup Optimization | AndroidX App Startup `PdfBoxInitializer` for lazy, non-blocking engine init | App launch context | Initialized PDFBox loader | Handled in background without UI freeze | `com.example.initializer.PdfBoxInitializer` |
| 9 | R1: Architecture | Result Wrapper Pattern | Sealed `Result<T>` (`Success`, `Error`, `Loading`) for uniform repository returns | Async repository calls | `Result<T>` flow | Catch block returns `Result.Error(exception)` | `com.example.util.Result.kt` |
| 10 | R1: Architecture | Repository Pattern with Room & Flow | `PasswordRepository` abstracting Room DAO with coroutines and Flows | SQL entity queries | `Flow<Result<List<PasswordEntity>>>` | SQLite exceptions wrapped in `Result.Error` | `com.example.data.PasswordRepository` |
| 11 | R1: Architecture | Native In-App PDF Viewer | Zero-overhead native viewer using `PdfRenderer` and 12.5% heap LRU Bitmap Cache | Decrypted PDF Uri | Rendered page bitmaps | Displays error dialog on bad PDF/Uri | `com.example.ui.PdfViewer.kt` |
| 12 | R1: Architecture | Shortcuts & Quick Settings Tile | App shortcuts (`ACTION_SELECT_PDF`, `ACTION_SHOW_SAVED_PASSWORDS`) and QS Tile | System launcher / QS tile tap | Direct activity routing | Falls back to main screen if intent empty | `AndroidManifest.xml` & `PdfDecryptorTileService` |
| 13 | R2: Security | Hardware Keystore Encryption | AES-GCM-256 encryption for saved passwords in local Room DB | Plaintext password string | `ENC_` prefixed Base64 ciphertext | Fallback to plaintext if keystore fails | `com.example.util.CryptoManager` |
| 14 | R2: Security | StrongBox Keymaster Detection | Hardware security module detection (`FEATURE_STRONGBOX_KEYSTORE`) | System PackageManager | Hardware-backed key generation | Graceful fallback to TEE Keymaster | ORIGINAL_REQUEST.md & Git commit `7885cb9` |
| 15 | R2: Security | BiometricPrompt with CryptoObject | Biometric authentication with cipher binding for password vault access | User biometric input | Authenticated cipher / vault access | Error / Cancel returns user to locked state | ORIGINAL_REQUEST.md & Git commit `7885cb9` |
| 16 | R2: Security | Biometric Unlock Fallback | PIN/Pattern/Password fallback (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) | Device credentials | Vault unlock permission | Auth failure denies access | `app/build.gradle.kts` & `MainActivity.kt` |
| 17 | R2: Security | DoD 5220.22-M File Shredding | Multi-pass random-byte overwrite before file deletion (`FileUtils.secureDelete`) | File handle | Overwritten file deleted from disk | Continues deletion if overwrite errors | `com.example.util.FileUtils.secureDelete` |
| 18 | R2: Security | Sensitive Password Memory Zeroization | Overwrite char arrays and byte buffers with 0s after PDFBox authentication | In-memory password buffers | Cleared memory buffers | N/A | ORIGINAL_REQUEST.md & Git commit `c86a925` |
| 19 | R2: Security | WindowManager FLAG_SECURE | `FLAG_SECURE` applied to window during sensitive password entry | Activity lifecycle events | Screen capture / preview blocked | Cleared on resume/dismiss | Git commit `8411d15` & `MainActivity.kt` |
| 20 | R2: Security | WorkManager Background Decryption | Background `CoroutineWorker` for batch processing large document sets | List of PDF Uris, output dir | Background worker task | Worker retry / failed state notification | ORIGINAL_REQUEST.md |
| 21 | R2: Security | Ongoing Progress Notifications | Foreground notification showing real-time batch decryption progress & cancel | Progress callbacks (current/total) | System status bar notification | Dismissed on completion or cancel | ORIGINAL_REQUEST.md & `BatchProcessUseCase.kt` |
| 22 | R2: Security | SAF Scoped Storage Guard | Strict URI permission flags and optimized tree child resolution | SAF Tree Uri, Document Uri | Safe I/O stream access | Returns null / `errorOutputDir` on permission failure | `com.example.util.FileUtils` |
| 23 | R2: Security | Cloud Backup Exclusion Rules | Disallow ADB / cloud backup of database and crypto keys | Android backup manager | Protected sandbox storage | Blocked per manifest rules | `xml/data_extraction_rules.xml` |
| 24 | R2: Security | Auto-Clear Password Timeout | Auto-clear entered password from memory after 60s inactivity or on background | Inactivity timer / `onPause` | Reset password StateFlow | State reset to empty string | Git commit `7885cb9` & `MainActivity.kt` |
| 25 | R3: Android 15 | Target SDK 35 Compliance | Build and runtime targeting Android 15 (API level 35, `VanillaIceCream`) | TargetSdk configuration | API 35 runtime compliance | Strict enforcement of Android 15 policies | `app/build.gradle.kts` line 24 |
| 26 | R3: Android 15 | Predictive Back Gestures | Support for Android 14/15 predictive back animations and dialog dismissals | Back gesture navigation | Smooth predictive transition | Default system back handling | `AndroidManifest.xml` & `MainActivity.kt` |
| 27 | R3: Android 15 | Edge-to-Edge Window Insets | Full bleed layout with `enableEdgeToEdge()` and `WindowInsets.safeDrawing` | System insets | Content padded around status/nav bars | Overlap avoided via insets padding | `MainActivity.kt` lines 77 & 84 |
| 28 | R3: Android 15 | Dynamic IME Keyboard Insets | Automatic UI repositioning when soft keyboard opens (`Modifier.imePadding()`) | Keyboard open/close events | Repositioned text fields | Preserves button visibility during typing | ORIGINAL_REQUEST.md & `MainActivity.kt` |
| 29 | R3: Android 15 | 16KB ELF Page Size Compliance | JNI libraries packaged with 16KB ELF page size alignment for Android 15 hardware | Native .so binaries | Compatible binary execution | Prevents crash on 16KB page size devices | `app/build.gradle.kts` & Git commit `c1ab8c2` |
| 30 | R3: Android 15 | Adaptive WindowSizeClass Support | Responsive UI adaptivity for Compact, Medium, and Expanded width classes | `WindowWidthSizeClass` | Responsive layout configurations | Dynamic reflow on orientation change | `MainActivity.kt` line 80 |
| 31 | R3: Android 15 | Tablet Dual-Pane Layout | Side-by-side master-detail layout on tablet/expanded displays | Expanded width screen | Dual-pane Composable screen | Gracefully collapses to single pane on phone | ORIGINAL_REQUEST.md & Git commit `c86a925` |
| 32 | R3: Android 15 | Foldable Tabletop Posture | Jetpack WindowManager posture detection for half-folded tabletop orientation | Folding hinge angle | Controls relocated to lower half | Standard layout on flat posture | ORIGINAL_REQUEST.md |
| 33 | R3: Android 15 | Drag and Drop PDF Ingestion | Direct dragging of PDF files from multi-window apps onto PDDF drop zone | Compose `dragAndDropTarget` | Ingested PDF Uri list | Unsupported mime types rejected with feedback | `MainActivity.kt` lines 55–58 |
| 34 | R3: Android 15 | AMOLED Pure Black & M3 Dynamic Color | Material 3 theme switcher with Dynamic Colors, Light, Dark, and AMOLED modes | `ThemeMode` preference | Applied `ColorScheme` | Falls back to static theme on older OS | `com.example.ui.theme.Theme.kt` |
| 35 | R3: Android 15 | Contextual Rich Haptic Feedback | Tactile vibration patterns for selection, delete, text handle move, and click | User UI interactions | `HapticFeedbackType` execution | Silently ignored if device lacks vibrator | `MainActivity.kt` & Git commit `fb4de36` |
| 36 | R4: i18n/a11y/test | Android Plurals Resource Setup | Implement `<plurals>` in `strings.xml` for all count-based strings | Integer count | Pluralized string resource | Fallback to `other` on unmapped quantity | ORIGINAL_REQUEST.md § R4 |
| 37 | R4: i18n/a11y/test | Compose Plurals Integration | Use `pluralStringResource(R.plurals.xxx, count, count)` in Composable UI | Count parameter | Localized plural text string | Compiles cleanly with zero type errors | AGENTS.md § 3 & ORIGINAL_REQUEST.md |
| 38 | R4: i18n/a11y/test | Traditional Chinese (`zh-rTW`) | Full Traditional Chinese translations, format specifiers, and plurals | Locale `zh-rTW` | Fully translated UI | Fallback to default English if key missing | `res/values-zh-rTW/strings.xml` |
| 39 | R4: i18n/a11y/test | Simplified Chinese (`zh-rCN`) | Create complete `values-zh-rCN/strings.xml` with translated strings & plurals | Locale `zh-rCN` | Simplified Chinese UI | Fallback to default English if key missing | ORIGINAL_REQUEST.md § R4 |
| 40 | R4: i18n/a11y/test | Japanese (`ja`) Localization | Complete Japanese translations and plurals in `values-ja/strings.xml` | Locale `ja` | Japanese UI | Fallback to default English if key missing | `res/values-ja/strings.xml` |
| 41 | R4: i18n/a11y/test | Spanish (`es`) Localization | Complete Spanish translations and plurals in `values-es/strings.xml` | Locale `es` | Spanish UI | Fallback to default English if key missing | `res/values-es/strings.xml` |
| 42 | R4: i18n/a11y/test | Multi-Locale Gradle & Locales Config | Update `localeFilters` and `locales_config.xml` with all 5 supported locales | Build config & manifest | Packaged multi-language resources | Unspecified locales stripped | `build.gradle.kts` & `locales_config.xml` |
| 43 | R4: i18n/a11y/test | TalkBack Semantics & Labels | Localized, contextual `contentDescription` on all interactive and toggle elements | UI state / toggle state | Screen reader accessibility output | Null description for decorative icons | AGENTS.md § 5 & ORIGINAL_REQUEST.md |
| 44 | R4: i18n/a11y/test | WCAG 2.1 AA 48dp Touch Targets | Enforce 48dp x 48dp minimum touch target size on all clickable elements | Touch gestures | Accessible touch interaction | Eliminates accidental misclicks | AGENTS.md § 5 & ORIGINAL_REQUEST.md |
| 45 | R4: i18n/a11y/test | Roborazzi Visual Regression Suite | Automated multi-device screenshot tests (Pixel 8, Pixel 4a, Fold, Tablet) | Roborazzi JUnit rule | Screenshot PNG image comparisons | Fails build if visual regression detected | `MultiDeviceScreenshotTest.kt` |
| 46 | R4: i18n/a11y/test | Turbine StateFlow Test Harness | Add `app.cash.turbine:turbine` for deterministic reactive Flow verification | StateFlow emissions | Step-by-step emission assertions | Fails on unexpected / missing emission | ORIGINAL_REQUEST.md § R4 |
| 47 | R4: i18n/a11y/test | Clean Arch Boundary & JVM Tests | Boundary tests verifying pure domain layer & full suite passing cleanly | JVM unit test runner | Test report / execution status | 0 test failures required | AGENTS.md § 6 & ORIGINAL_REQUEST.md |

---

## 4. Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | Plurals Localization | Count = 0, 1, 5 in English vs Chinese vs Japanese | English/Spanish requires `one` and `other`; Chinese/Japanese requires `other`. Hardcoded format specifiers without `<plurals>` produce ungrammatical `"1 file(s)"` or `"已選擇 1 個檔案(s)"`. |
| 2 | Locale Configuration | Locale `zh-CN`, `ja`, or `es` when `localeFilters` is restricted | Android builds strip unlisted locale directories, causing the OS to fall back to English even when translation files exist in source tree. |
| 3 | Accessibility (TalkBack) | Password toggle button clicked repeatedly | Content description must dynamically flip between localized `"Show password"` and `"Hide password"` rather than static text. |
| 4 | TalkBack List Deletion | Multiple passwords in Saved Passwords list | `contentDescription` must provide `"Delete [Password Name]"` rather than generic `"Delete"` to avoid screen reader ambiguity. |
| 5 | Touch Target Sizing | Compact screen with small icon buttons | Custom clickable rows under 48dp fail WCAG 2.1 AA and are difficult for motor-impaired users to tap. |
| 6 | Turbine StateFlow Testing | Fast consecutive state changes in ViewModel | Standard `.first()` only observes the immediate or latest state; Turbine `.test { ... }` validates the entire sequence of intermediate emissions (`Loading` -> `Processing` -> `Success`). |
| 7 | Architecture Boundary Rules | UI / Android framework imports in Domain package | Violates Clean Architecture by tightly coupling business logic to Android runtime; detected by architectural unit tests. |
| 8 | Large Batch Decryption | 100+ PDF files processed on Main Thread | Triggers ANR (Application Not Responding) dialog. Strict isolation to `Dispatchers.IO` and WorkManager ensures zero UI frame drops. |
| 9 | Coroutine Cancellation | User cancels batch decryption mid-progress | `ensureActive()` and `CancellationException` catch blocks securely wipe temporary files via `FileUtils.secureDelete` and report `cancelledCount`. |
| 10 | Real Encrypted PDF with DRM / Certificate | Public key or certificate encrypted PDF | Standard password fails with `DecryptStatus.UNSUPPORTED_ENCRYPTION` and informs user clearly rather than hanging or crashing. |

---

## 5. Caveats

1. **Read-Only Inspection**: In accordance with the Survey Spec Miner instructions, no source code files or resource XMLs were modified. All gaps noted above represent concrete tasks for implementation milestones.
2. **JaCoCo Classpath**: The `jacocoTestReport` task will require updating the `classDirectories` fileTree to include Gradle's current compiler output paths before generating non-empty HTML coverage statistics.
3. **Hardware Biometrics in Robolectric**: Real hardware biometric sensor operations and StrongBox Keymaster require physical hardware or emulator instrumentation tests (`connectedAndroidTest`); Robolectric executes unit tests using JVM mocks and `CryptoManager.testKeyOverride`.

---

## 6. Conclusion

1. **R4 Gap Summary**:
   - `<plurals>` must be added across all languages.
   - `values-zh-rCN` must be created.
   - `localeFilters` in `build.gradle.kts` and `locales_config.xml` must be expanded to include all target languages (`zh-rTW`, `zh-rCN`, `ja`, `es`, `en`).
   - Hardcoded English strings in Composables must be migrated to `strings.xml`.
   - TalkBack dynamic labels, WCAG 48dp touch targets, and Compose semantics must be added.
   - Turbine (`app.cash.turbine:turbine`) and Clean Architecture boundary test suites must be introduced.
2. **47 Patches Inventory**: All 47 Jules modernization patches across R1 (12 patches), R2 (12 patches), R3 (11 patches), and R4 (12 patches) have been fully discovered, categorized, and documented with inputs, outputs, error behaviors, and discovery sources.
3. **Current Test Status**: Current baseline test suite is **100% healthy** with **52 passing tests across 15 test classes** and zero test failures.

---

## 7. Verification Method

To independently reproduce and verify the findings:

1. **Verify JVM Unit Tests**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat :app:testDebugUnitTest --rerun-tasks
   ```
   *Expected Result*: `BUILD SUCCESSFUL`, 52 passing tests recorded in `app/build/test-results/testDebugUnitTest/`.

2. **Verify Localization Resources & Missing `zh-rCN`**:
   ```powershell
   Get-ChildItem -Path "app/src/main/res" -Directory -Filter "values*"
   ```
   *Expected Result*: Lists `values`, `values-de`, `values-es`, `values-fr`, `values-ja`, `values-zh-rTW`. Notice `values-zh-rCN` is absent.

3. **Verify Absence of `<plurals>`**:
   ```powershell
   Select-String -Path "app/src/main/res/values*/strings.xml" -Pattern "<plurals"
   ```
   *Expected Result*: 0 matches returned.

4. **Verify Locale Filters in Gradle**:
   Inspect line 30 of `app/build.gradle.kts` to observe `localeFilters += listOf("zh-rTW", "en")`.
