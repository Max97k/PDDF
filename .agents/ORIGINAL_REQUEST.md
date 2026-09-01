# Original User Request

## 2026-09-01T04:27:39Z

Integrate and verify 47 Jules modernization patches into the Android app codebase (Max97k/PDDF), establishing clean architecture modularization, hardware security, Android 15 compliance, adaptive UI, and a robust automated test suite.

Working directory: c:\Users\b\PDDF
Integrity mode: development

## Requirements

### R1. Architecture & UI Modularization
Deconstruct monolithic activities into structured packages (ui/components, feature/vault, feature/viewer, feature/decrypt), establishing a single immutable UI state (MainUiState) with unidirectional data flow (UDF) and single-shot event streams (UiEffect).

### R2. Hardware Security & Background Processing
Integrate hardware-backed BiometricPrompt (CryptoObject), StrongBox Keymaster detection, DoD temporary file shredding, sensitive password memory zeroization, and AndroidX WorkManager background batch decryption workers with ongoing progress notifications.

### R3. Android 15 & Adaptive Form Factors
Implement Android 15 (API 35) predictive back gesture transitions, edge-to-edge window insets with IME keyboard padding, 16KB ELF page size compliance, tablet dual-pane layout, and foldable tabletop mode awareness.

### R4. Internationalization, Accessibility & Testing
Incorporate Android Plurals, complete multi-language localization (Traditional Chinese, Simplified Chinese, Japanese, Spanish), TalkBack screen reader semantics with WCAG 2.1 AA 48dp touch targets, and comprehensive automated test coverage (Roborazzi visual regression, Turbine StateFlow, and Clean Architecture boundary rules).

## Acceptance Criteria

### Build & Verification Guardrails
- [ ] All JVM unit tests pass cleanly via .\gradlew.bat :app:testDebugUnitTest with zero test failures.
- [ ] Code builds cleanly with zero compilation errors.
- [ ] Zero file I/O or PDF parsing executed on the Main Thread (strict Dispatchers.IO isolation).
- [ ] All existing core functionalities (PDF auto-unlock, batch decryption, password vault, launcher shortcuts) remain fully functional without regressions.
