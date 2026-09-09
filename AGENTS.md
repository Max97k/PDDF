# AGENTS.md — Android Development Guidelines & Project Specifications

This document establishes the official development standards, architectural contracts, and operational guardrails for AI coding agents working in this repository using Antigravity and Android Studio on Windows.

---

## 1. Environment & Build Tooling (Windows / PowerShell)
- **Target Platform**: Android (Min SDK 24, Target SDK 35)
- **Host OS**: Windows (Shell: PowerShell)
- **Gradle Command Standard**:
  - Always use `.\gradlew.bat` for CLI executions (e.g., `.\gradlew.bat assembleDebug`).
  - Never execute POSIX/Linux-specific commands (e.g., `chmod`, `./gradlew`, `export`).
- **Path Boundary**: Operations must remain strictly within the workspace project root. Do not read or leak parent directories or external credentials.

---

## 2. Architecture & State Management (Modern Android / MAD)
- **Pattern**: MVVM with Unidirectional Data Flow (UDF).
- **State Exposure**:
  - ViewModels expose state via `StateFlow<UiState>` as a single, immutable `data class`.
  - In Compose, collect states using `collectAsStateWithLifecycle()`.
  - UI events flow upwards via lambda callbacks.
- **Dependency Flow**: UI Layer (`@Composable`) -> ViewModel -> Repository (Single Source of Truth) -> Data Source (Room / SAF / Network).
- **Fragment & Window Isolation Contract**:
  - NEVER host an `AndroidView(factory = { FragmentContainerView(...) })` inside an `androidx.compose.ui.window.Dialog`. Compose `Dialog` creates an independent secondary `Window` and `DecorView`, causing host `FragmentActivity.supportFragmentManager.commit` to crash with `IllegalArgumentException: No view found for id 0x...`.
  - Always use an in-window overlay pattern (`Box(modifier = Modifier.fillMaxSize().zIndex(100f))` with `BackHandler`).
  - Application Theme MUST inherit from Material3 (`Theme.Material3.DayNight.NoActionBar`). AndroidX `PdfViewerFragment`'s internal layout (`pdf_viewer_fragment.xml`) mandates Material 3 theme attributes; running under `DeviceDefault` crashes with `InflateException`.

---

## 3. Jetpack Compose Standards (Google API Guidelines)
- **Composable Signatures**:
  - Functions emitting UI must be named in `PascalCase` and return `Unit`.
  - The `modifier: Modifier = Modifier` parameter must always be the **first optional parameter**.
- **Performance & Recomposition**:
  - Never pass ViewModels deeply into child leaf composables; pass only raw state and lambdas.
  - In `LazyColumn` / `LazyRow`, always provide an explicit `key = { ... }`.
  - Use `derivedStateOf` when state changes faster than UI needs to redraw.
- **Resource Management**:
  - Never hardcode user-facing strings in Composables. Use `stringResource(R.string.xxx)` and sync both `res/values/strings.xml` and `res/values-zh-rTW/strings.xml`.

---

## 4. Concurrency & I/O Isolation (Performance & Thread Safety)
- **Zero I/O on Main Thread**:
  - `ContentResolver` queries, SAF operations, and Apache PDFBox parsing MUST run on `Dispatchers.IO`.
  - Never execute file/content queries directly inside `@Composable` rendering branches. Wrap in `LaunchedEffect` or ViewModel Coroutines.
- **Auto-Closeable Streams & SAF Overwrite Truncation**:
  - Always use Kotlin `.use { ... }` blocks when handling `InputStream`, `OutputStream`, `ParcelFileDescriptor`, or `PDDocument` instances to avoid memory leaks.
  - When overwriting files via SAF `ContentResolver.openOutputStream(uri)`, ALWAYS specify mode `"wt"` (write + truncate). Using default `"w"` appends/preserves old trailing bytes, corrupting PDF file headers/trailers.

---

## 5. Accessibility & UX Standards (TalkBack / Palette Rules)
- Purely decorative icons or icons adjacent to descriptive text must have `contentDescription = null`.
- Contextual actions in lists must provide specific labels (e.g., `"Delete ${item.name}"`).
- Toggle states (e.g., password visibility) must dynamically update description (`"Show password"` vs `"Hide password"`).

---

## 6. Verification & Definition of Done (MANDATORY TESTING STANDARD)
Before declaring any task complete, every AI Agent must execute and strictly verify:

1. **JVM Unit & Regression Tests**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest
   ```
   Must pass 100% (all test suites clean, 0 failures, 0 skipped).

2. **Code Coverage & Build Check**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:jacocoTestReport
   ```

3. **MANDATORY On-Device / Emulator E2E Automated Verification**:
   > [!CRITICAL]
   > **NEVER claim "E2E verified" based solely on JVM unit tests (`testDebugUnitTest`).**  
   > JVM/Robolectric executes in headless host memory with a mocked WindowManager and mocked layout inflator. It CANNOT detect:
   > - Compose Dialog window isolation mismatch (`No view found for id 0x1`)
   > - XML theme attribute resolution failures (`InflateException`)
   > - Asynchronous FragmentManager transaction lifecycle races
   > - SAF stream truncation bugs on actual DocumentsProviders.

   For ANY changes affecting UI flows, SAF file operations, Fragment embedding, or PDF rendering, the Agent MUST execute the unattended E2E automation script on a running emulator or connected device:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify_pdf_viewer.ps1 -DeviceId emulator-5554
   ```
   **Pass Criteria for E2E Script (Exit Code 0)**:
   - [PASS] Push real AES-256 encrypted PDF asset (`app/src/test/assets/encrypted.pdf`) to `/sdcard/Download/`
   - [PASS] Clean state reset (`pm clear com.max97k.pddf`) and app launch
   - [PASS] Auto-dismiss intro dialogs and select target encrypted file in `documentsui`
   - [PASS] Auto-type password `"password"` and click "Overwrite Original"
   - [PASS] Assert decryption success and render AndroidX `PdfViewerFragment`
   - [PASS] Assert Share button shifts focus to system `ChooserActivity` and cleanly returns
   - [PASS] Assert Save As button shifts focus to `documentsui` and cleanly returns
   - [PASS] Assert Close button destroys PDF viewer and restores main screen
   - [PASS] Assert Logcat continuous monitoring has **0 Fatal Exceptions / 0 Runtime Crashes**.

4. **Context Optimization**: When reading source files, use line slicing (`StartLine` / `EndLine`) to prevent context window bloat.
