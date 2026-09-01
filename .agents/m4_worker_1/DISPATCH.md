## 2026-09-01T06:11:18Z
You are Worker for Milestone 4 (R4: Internationalization, Accessibility & Testing Implementation).
Your Working Directory: c:\Users\b\PDDF\.agents\m4_worker_1

IMPORTANT: You are a DIRECT CODE-WRITING WORKER. You must directly edit files and run commands using your tools (`write_to_file`, `replace_file_content`, `run_command`, `view_file`). Do NOT spawn subagents.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Context & Reference Files:
- c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md
- c:\Users\b\PDDF\AGENTS.md
- c:\Users\b\PDDF\PROJECT.md
- c:\Users\b\PDDF\TEST_INFRA.md
- c:\Users\b\PDDF\.agents\explorer_gen2_3\handoff.md

Your Tasks:
1. **Simplified Chinese Localization**:
   - Create `app/src/main/res/values-zh-rCN/strings.xml` containing complete Simplified Chinese translations matching all string keys in `res/values/strings.xml` with proper format specifiers.
2. **Android Plurals Setup**:
   - Add `<plurals>` definitions in `res/values/strings.xml` (or `res/values/plurals.xml`), `res/values-zh-rTW/strings.xml`, `res/values-zh-rCN/strings.xml`, `res/values-ja/strings.xml`, `res/values-es/strings.xml`, `res/values-de/strings.xml`, `res/values-fr/strings.xml`:
     - `<plurals name="selected_files_count">` (one: "%1$d file selected", other: "%1$d files selected")
     - `<plurals name="processing_files_count">` (one: "Decrypting %1$d file...", other: "Decrypting %1$d files...")
3. **Locale Config**:
   - Update `app/src/main/res/xml/locales_config.xml` to include `<locale android:name="en"/>`, `<locale android:name="zh-TW"/>`, `<locale android:name="zh-CN"/>`, `<locale android:name="ja"/>`, `<locale android:name="es"/>`, `<locale android:name="de"/>`, `<locale android:name="fr"/>`.
4. **Refactor Composable UI Strings & Accessibility**:
   - In `app/src/main/java/com/example/ui/components/SelectedFilesCard.kt`: use `pluralStringResource(R.plurals.selected_files_count, fileCount, fileCount)`.
   - In `app/src/main/java/com/example/ui/components/PasswordInputSection.kt`: use `stringResource` for password visibility description (`show_password` / `hide_password`).
   - In `app/src/main/java/com/example/ui/components/DocumentDetailsCard.kt`: replace hardcoded strings ("Document Details", expand/collapse description, "Title: %s", "Author: %s", "Pages: %s", "File Size: %s", "Encryption: %s", "Permissions: %s") with `stringResource`.
   - In `app/src/main/java/com/example/ui/components/ThemeDropdownMenu.kt`: replace hardcoded strings ("Theme Settings", "System Default", "Light", "Dark", "AMOLED Black") with `stringResource`.
   - In `app/src/main/java/com/example/feature/decrypt/BatchProgressDialog.kt`: replace hardcoded strings ("Processing Batch", progress format) with `stringResource` / `pluralStringResource`.
   - In `app/src/main/java/com/example/feature/decrypt/AutoUnlockPasswordDialog.kt`: use `stringResource` for visibility toggle, add `Modifier.defaultMinSize(minHeight = 48.dp)` or `Modifier.minimumInteractiveComponentSize()` on remember password row.
   - In `app/src/main/java/com/example/feature/vault/SavedPasswordListDialog.kt`: replace hardcoded strings ("Search", "Clear search", "No matching passwords") with `stringResource`.
5. **Test Synchronization**:
   - Update `app/src/test/java/com/example/LocalizationAndPluralsTest.kt` to include `values-zh-rCN/`, verify all plurals, verify format specifier parity.
   - Update `app/src/test/java/com/example/ComposeUiTests.kt` to align with the localized resources.
6. **Verification**:
   - Run: `powershell` with `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`
   - Ensure all unit tests pass with 0 failures.
   - Write handoff report to `c:\Users\b\PDDF\.agents\m4_worker_1\handoff.md` and send completion message.
