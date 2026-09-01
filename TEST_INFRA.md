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
- Test Runner: Gradle command `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`
- Visual Regression: Roborazzi (`.\gradlew.bat :app:recordRoborazziDebug` / `verifyRoborazziDebug`)
- Coverage: JaCoCo (`.\gradlew.bat :app:jacocoTestReport`)
- Test Directory Layout: `app/src/test/java/com/example/`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Auto-unlock encrypted PDF with saved StrongBox Keystore password on app launch | Vault, Crypto, AutoUnlock, Threading | High |
| 2 | Batch decrypt 10 encrypted PDFs with conflict copy mode & cancel mid-stream | BatchProcess, DoD Shredding, WorkManager, Notification | High |
| 3 | Multi-window drag-and-drop on foldable/tablet dual-pane with IME keyboard typing | DragDrop, WindowInsets, AdaptiveLayout, UDF | High |
| 4 | Multi-language switching (zh-TW, zh-CN, ja, es) with plural counts and screen reader | Localization, Plurals, TalkBack, WCAG | Medium |
| 5 | Reactive StateFlow emission stream verification under rapid concurrent user actions | MainViewModel, MainUiState, Turbine, Clean Architecture | High |
