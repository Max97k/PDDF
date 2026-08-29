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
- **Auto-Closeable Streams**:
  - Always use Kotlin `.use { ... }` blocks when handling `InputStream`, `OutputStream`, `ParcelFileDescriptor`, or `PDDocument` instances to avoid memory leaks.

---

## 5. Accessibility & UX Standards (TalkBack / Palette Rules)
- Purely decorative icons or icons adjacent to descriptive text must have `contentDescription = null`.
- Contextual actions in lists must provide specific labels (e.g., `"Delete ${item.name}"`).
- Toggle states (e.g., password visibility) must dynamically update description (`"Show password"` vs `"Hide password"`).

---

## 6. Verification & Definition of Done
Before completing any task, the Agent must execute and verify:
1. **JVM Unit Tests**:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
2. **Code Coverage / Build Check** (when adding features):
   ```powershell
   .\gradlew.bat :app:jacocoTestReport
   ```
3. **Context Optimization**: When reading source files, use line slicing (`StartLine` / `EndLine`) to prevent context window bloat.
