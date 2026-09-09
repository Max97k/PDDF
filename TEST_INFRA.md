# E2E Test Infra: Max97k/PDDF Modernization

## Test Philosophy
- Opaque-box, requirement-driven derived directly from `ORIGINAL_REQUEST.md`.
- Methodology: Category-Partition (Tier 1) + Boundary Value Analysis (Tier 2) + Pairwise Combinatorial (Tier 3) + Real-World Workloads (Tier 4).
- Target: 100% pass rate on JVM tests via `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`.

## Feature Inventory & Test Matrix
| # | Feature Area | Requirement Reference | Tier 1 (Feature) | Tier 2 (Boundary) | Tier 3 (Pairwise) | Tier 4 (Scenario) |
|---|--------------|-----------------------|:----------------:|:-----------------:|:-----------------:|:-----------------:|
| 1 | Architecture & UDF State | ORIGINAL_REQUEST § R1 | 5 | 5 | ✓ | ✓ |
| 2 | Biometrics & CryptoObject | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| 3 | StrongBox Keymaster | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| 4 | DoD File Shredding & Memory Wipe | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| 5 | WorkManager & Notifications | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| 6 | Android 15 Predictive Back & Insets | ORIGINAL_REQUEST § R3 | 5 | 5 | ✓ | ✓ |
| 7 | 16KB Page Size & JNI Packaging | ORIGINAL_REQUEST § R3 | 5 | 5 | ✓ | ✓ |
| 8 | Adaptive Tablet & Foldable UI | ORIGINAL_REQUEST § R3 | 5 | 5 | ✓ | ✓ |
| 9 | Android Plurals & Multi-Locale | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |
| 10| TalkBack & WCAG 48dp Touch Targets | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |
| 11| Roborazzi Visual Regression | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |
| 12| Turbine & Clean Architecture Boundary | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |

## Test Architecture
- Tier 1–4 JVM Runner: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest` (32 suites, 166 tests passing).
- Tier 5 On-Device/Emulator E2E Automation: `powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify_pdf_viewer.ps1 -DeviceId emulator-5554`
- Visual Regression: Roborazzi (`.\gradlew.bat :app:recordRoborazziDebug` / `verifyRoborazziDebug`)
- Coverage: JaCoCo (`.\gradlew.bat :app:jacocoTestReport`)
- Test Directory Layout: `app/src/test/java/com/example/` & `scripts/`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Auto-unlock encrypted PDF with saved StrongBox Keystore password on app launch | Vault, Crypto, AutoUnlock, Threading | High |
| 2 | Batch decrypt 10 encrypted PDFs with conflict copy mode & cancel mid-stream | BatchProcess, DoD Shredding, WorkManager, Notification | High |
| 3 | Multi-window drag-and-drop on foldable/tablet dual-pane with IME keyboard typing | DragDrop, WindowInsets, AdaptiveLayout, UDF | High |
| 4 | Multi-language switching (zh-TW, zh-CN, ja, es) with plural counts and screen reader | Localization, Plurals, TalkBack, WCAG | Medium |
| 5 | Reactive StateFlow emission stream verification under rapid concurrent user actions | MainViewModel, MainUiState, Turbine, Clean Architecture | High |

## Tier 5: On-Device / Emulator Automated E2E Verification
> [!IMPORTANT]
> JVM tests cannot catch Window isolation bugs (`No view found for id 0x1`) or XML theme attribute inflation bugs (`InflateException`). Any task affecting UI, SAF, or PDF Viewer MUST run Tier 5 automated verification:

- **Script**: `scripts/verify_pdf_viewer.ps1`
- **Execution Target**: Android Emulator API 35+ (`emulator-5554`) or connected physical device.
- **Workflow**:
  1. Push real AES-256 test asset (`app/src/test/assets/encrypted.pdf`) to `/sdcard/Download/`
  2. Clear app state (`pm clear com.max97k.pddf`) and launch `MainActivity`
  3. Dismiss "What's New" intro dialog
  4. Select `encrypted.pdf` via `documentsui`
  5. Type password `"password"` and tap "Overwrite Original" (with `"wt"` stream truncation)
  6. Assert decryption card and tap "Preview PDF"
  7. Verify AndroidX `PdfViewerFragment` renders document
  8. Test Share button -> assert focus switches to `ChooserActivityLauncher` -> Back
  9. Test Save As button -> assert focus switches to `documentsui` -> Back
  10. Test Close button -> assert viewer destroyed and main screen restored
  11. Continuous Logcat crash watcher -> assert 0 Fatal Exceptions / 0 Runtime Crashes.

