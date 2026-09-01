## 2026-09-01T07:00:18Z
You are Worker 3 for Milestone 4 (R4: Internationalization, Accessibility & Testing Final Verification).
Your Working Directory: c:\Users\b\PDDF\.agents\m4_worker_3

IMPORTANT: You are a DIRECT WORKER. You must directly edit/view files and run commands using your tools (`write_to_file`, `replace_file_content`, `run_command`, `view_file`). Do NOT spawn subagents.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Context & Reference Files:
- c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md
- c:\Users\b\PDDF\AGENTS.md
- c:\Users\b\PDDF\PROJECT.md
- c:\Users\b\PDDF\TEST_INFRA.md

Your Tasks:
1. Check that `app/src/main/res/values-zh-rCN/strings.xml` exists and has complete Simplified Chinese translations and `<plurals>`.
2. Check that `app/src/main/res/xml/locales_config.xml` includes all 7 supported locales.
3. Check that Composable UI files (`SelectedFilesCard.kt`, `PasswordInputSection.kt`, `DocumentDetailsCard.kt`, `ThemeDropdownMenu.kt`, `BatchProgressDialog.kt`, `AutoUnlockPasswordDialog.kt`, `SavedPasswordListDialog.kt`) use `stringResource` / `pluralStringResource`.
4. Run Gradle test command:
   Command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.LocalizationAndPluralsTest" --tests "com.example.CleanArchitectureBoundaryTest" --tests "com.example.MainViewModelUdfTurbineTest"`
   and the full test suite:
   `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`
   Ensure 100% tests pass cleanly with 0 failures.
5. Write your handoff report to `c:\Users\b\PDDF\.agents\m4_worker_3\handoff.md` and send completion message to parent immediately.
