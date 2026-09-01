# Project: Max97k/PDDF Modernization

## Architecture
- Pattern: Clean Architecture + MVVM with Unidirectional Data Flow (UDF)
- UI Layer: Jetpack Compose (Material 3), single immutable `MainUiState`, one-off `UiEffect` event channel
- Domain Layer: Isolated UseCases (`DecryptPdfUseCase`, `AutoUnlockUseCase`, `BatchProcessUseCase`, `PasswordVaultUseCase`), pure Kotlin without Android UI framework dependencies
- Data Layer: Room DB with AES-GCM-256 KeyStore encryption, `PasswordRepository`, DataStore `ThemePreferences`, `WorkManager` background workers
- Threading: Strict `Dispatchers.IO` isolation for all file I/O, ContentResolver operations, and PDFBox parsing

## Code Layout
```
app/src/main/java/com/example/
├── MainActivity.kt                      # Slim Activity: Lifecycle, Intent dispatch, System Insets, Root Scaffold
├── MainViewModel.kt                     # UDF ViewModel: StateFlow<MainUiState>, Channel<UiEffect>, UseCase orchestration
├── MainUiState.kt                       # Single immutable UI state data class
├── UiEffect.kt                          # Single-shot UI effects sealed interface
├── MainUiAction.kt                      # Sealed interface for upward UI intents
├── feature/
│   ├── decrypt/
│   │   ├── PDFDecryptorScreen.kt        # Primary decrypt screen composable (adaptive single/dual pane)
│   │   ├── BatchProgressDialog.kt       # Batch decryption progress dialog
│   │   └── AutoUnlockPasswordDialog.kt  # Auto-unlock password prompt dialog
│   ├── vault/
│   │   ├── SavedPasswordListDialog.kt   # Password vault list & search dialog
│   │   ├── SavePasswordDialog.kt        # Save password dialog
│   │   └── BiometricHelper.kt           # Hardware-backed BiometricPrompt with CryptoObject
│   └── viewer/
│       ├── PdfViewerDialog.kt           # Fullscreen PDF viewer dialog
│       ├── PdfViewerScreen.kt           # PDF viewer screen composable (async I/O on Dispatchers.IO)
│       └── PdfPageItem.kt               # Individual rendered page with LRU cache
├── ui/
│   ├── components/
│   │   ├── SelectedFilesCard.kt         # Selected files display & clear card
│   │   ├── PasswordInputSection.kt      # Password input field with action buttons & IME padding
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
│   └── worker/ (BatchDecryptWorker.kt)
├── initializer/
│   └── PdfBoxInitializer.kt
└── util/
    ├── CryptoManager.kt (StrongBox + TEE KeyStore, CryptoObject cipher)
    ├── FileUtils.kt (DoD 5220.22-M 3-pass file shredding)
    ├── MemoryUtils.kt (CharArray / byte buffer zeroization)
    └── Result.kt
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Package Reorganization | Deconstruct monolithic `MainActivity.kt` into `ui/components/`, `feature/vault/`, `feature/viewer/`, `feature/decrypt/` | M1 | Survey / ORIGINAL_REQUEST |
| 2 | Unified MainUiState | Single immutable UI state data class replacing separate StateFlows | M1 | Survey / ORIGINAL_REQUEST |
| 3 | UiEffect Single-Shot Channel | Single-shot channel for one-off events (dialogs, picker, snackbars) | M1 | Survey / ORIGINAL_REQUEST |
| 4 | Clean Architecture UseCases | Isolated UseCases with pure domain boundaries | M1 | Survey / ORIGINAL_REQUEST |
| 5 | Composable Leaf Decoupling | Leaf composables accept only state and lambdas; ViewModels at screen root | M1 | Survey / AGENTS.md |
| 6 | Compose Stability & Immutability | `@Immutable` models and stable recomposition skips | M1 | Survey / AGENTS.md |
| 7 | Explicit LazyColumn Keys & Content Types | Deterministic keys and content types in all lists | M1 | Survey / AGENTS.md |
| 8 | App Startup Optimization | AndroidX App Startup `PdfBoxInitializer` for non-blocking engine init | M1 | Survey / Codebase |
| 9 | Result Wrapper Pattern | Sealed `Result<T>` (`Success`, `Error`, `Loading`) for uniform repository returns | M1 | Survey / Codebase |
| 10 | Repository Pattern with Room & Flow | `PasswordRepository` abstracting Room DAO with coroutines and Flows | M1 | Survey / Codebase |
| 11 | Native In-App PDF Viewer Threading | Zero-overhead native viewer on `Dispatchers.IO` with 12.5% heap LRU Cache | M1 | Survey / Codebase |
| 12 | Shortcuts & Quick Settings Tile | App shortcuts (`ACTION_SELECT_PDF`, `ACTION_SHOW_SAVED_PASSWORDS`) and QS Tile | M1 | Survey / Codebase |
| 13 | Hardware Keystore Encryption | AES-GCM-256 encryption for saved passwords in local Room DB | M2 | Survey / ORIGINAL_REQUEST |
| 14 | StrongBox Keymaster Detection | Hardware security module detection (`FEATURE_STRONGBOX_KEYSTORE`) with TEE fallback | M2 | Survey / ORIGINAL_REQUEST |
| 15 | BiometricPrompt with CryptoObject | Biometric authentication with cipher binding for password vault access | M2 | Survey / ORIGINAL_REQUEST |
| 16 | Biometric Unlock Fallback | PIN/Pattern/Password fallback (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) | M2 | Survey / Codebase |
| 17 | DoD 5220.22-M File Shredding | Multi-pass (0x00, 0xFF, random + sync) overwrite before file deletion | M2 | Survey / ORIGINAL_REQUEST |
| 18 | Sensitive Password Memory Zeroization | Overwrite char arrays and byte buffers with 0s after use and on backgrounding | M2 | Survey / ORIGINAL_REQUEST |
| 19 | WindowManager FLAG_SECURE | `FLAG_SECURE` applied to window during sensitive password entry | M2 | Survey / Codebase |
| 20 | WorkManager Background Decryption | Background `CoroutineWorker` for batch processing large document sets | M2 | Survey / ORIGINAL_REQUEST |
| 21 | Ongoing Progress Notifications | Foreground notification showing real-time batch decryption progress & cancel | M2 | Survey / ORIGINAL_REQUEST |
| 22 | SAF Scoped Storage Guard | Strict URI permission flags and optimized tree child resolution | M2 | Survey / Codebase |
| 23 | Cloud Backup Exclusion Rules | Disallow ADB / cloud backup of database and crypto keys | M2 | Survey / Codebase |
| 24 | Auto-Clear Password Timeout | Auto-clear entered password from memory after 60s inactivity or on background | M2 | Survey / Codebase |
| 25 | Target SDK 35 Compliance | Build and runtime targeting Android 15 (API level 35) | M3 | Survey / ORIGINAL_REQUEST |
| 26 | Predictive Back Gestures | Support for Android 14/15 predictive back animations and dialog dismissals | M3 | Survey / ORIGINAL_REQUEST |
| 27 | Edge-to-Edge Window Insets | Full bleed layout with `enableEdgeToEdge()` and `WindowInsets.safeDrawing` | M3 | Survey / ORIGINAL_REQUEST |
| 28 | Dynamic IME Keyboard Insets | Automatic UI repositioning when soft keyboard opens (`Modifier.imePadding()`) | M3 | Survey / ORIGINAL_REQUEST |
| 29 | 16KB ELF Page Size Compliance | JNI libraries packaged with 16KB ELF alignment (`useLegacyPackaging = false`) | M3 | Survey / ORIGINAL_REQUEST |
| 30 | Adaptive WindowSizeClass Support | Responsive UI adaptivity for Compact, Medium, and Expanded width classes | M3 | Survey / ORIGINAL_REQUEST |
| 31 | Tablet Dual-Pane Layout | Side-by-side master-detail layout on tablet/expanded displays | M3 | Survey / ORIGINAL_REQUEST |
| 32 | Foldable Tabletop Posture | Jetpack WindowManager posture detection for half-folded tabletop orientation | M3 | Survey / ORIGINAL_REQUEST |
| 33 | Drag and Drop PDF Ingestion | Direct dragging of PDF files from multi-window apps onto drop zone | M3 | Survey / Codebase |
| 34 | AMOLED Pure Black & M3 Dynamic Color | Material 3 theme switcher with Dynamic Colors, Light, Dark, and AMOLED modes | M3 | Survey / Codebase |
| 35 | Contextual Rich Haptic Feedback | Tactile vibration patterns for selection, delete, text handle move, and click | M3 | Survey / Codebase |
| 36 | Android Plurals Resource Setup | Implement `<plurals>` in `strings.xml` for all count-based strings | M4 | Survey / ORIGINAL_REQUEST |
| 37 | Compose Plurals Integration | Use `pluralStringResource(R.plurals.xxx, count, count)` in Composable UI | M4 | Survey / ORIGINAL_REQUEST |
| 38 | Traditional Chinese (`zh-rTW`) | Full Traditional Chinese translations, format specifiers, and plurals | M4 | Survey / ORIGINAL_REQUEST |
| 39 | Simplified Chinese (`zh-rCN`) | Create complete `values-zh-rCN/strings.xml` with translated strings & plurals | M4 | Survey / ORIGINAL_REQUEST |
| 40 | Japanese (`ja`) Localization | Complete Japanese translations and plurals in `values-ja/strings.xml` | M4 | Survey / ORIGINAL_REQUEST |
| 41 | Spanish (`es`) Localization | Complete Spanish translations and plurals in `values-es/strings.xml` | M4 | Survey / ORIGINAL_REQUEST |
| 42 | Multi-Locale Gradle & Locales Config | Update `localeFilters` and `locales_config.xml` with all 5 supported locales | M4 | Survey / ORIGINAL_REQUEST |
| 43 | TalkBack Semantics & Labels | Localized, contextual `contentDescription` on all interactive and toggle elements | M4 | Survey / ORIGINAL_REQUEST |
| 44 | WCAG 2.1 AA 48dp Touch Targets | Enforce 48dp x 48dp minimum touch target size on all clickable elements | M4 | Survey / ORIGINAL_REQUEST |
| 45 | Roborazzi Visual Regression Suite | Automated multi-device screenshot tests (Pixel 8, Pixel 4a, Fold, Tablet) | M4 | Survey / ORIGINAL_REQUEST |
| 46 | Turbine StateFlow Test Harness | Add `app.cash.turbine:turbine` for deterministic reactive Flow verification | M4 | Survey / ORIGINAL_REQUEST |
| 47 | Clean Arch Boundary & JVM Tests | Boundary tests verifying pure domain layer & full suite passing cleanly | M4 | Survey / ORIGINAL_REQUEST |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Architecture & UI Modularization | R1: Patches 1-12 (Package restructure, MainUiState, UiEffect, Leaf Composables, Threading I/O fix) | none | DONE |
| M2 | Hardware Security & Background Processing | R2: Patches 13-24 (Biometric CryptoObject, StrongBox, DoD shredding, zeroization, WorkManager) | M1 | DONE |
| M3 | Android 15 & Adaptive Form Factors | R3: Patches 25-35 (Predictive back, IME insets, 16KB ELF, Tablet dual-pane, Foldable tabletop) | M1 | DONE |
| M4 | Internationalization, Accessibility & Testing | R4: Patches 36-47 (Plurals, zh-rCN/ja/es, WCAG 48dp, TalkBack, Turbine, Clean Arch tests) | M1 | DONE |
| M5 | Final Milestone: 100% E2E Pass & Hardening | Phase 1 (Pass 100% E2E test suite Tiers 1-4) + Phase 2 (Adversarial Coverage Hardening Tier 5) | M1, M2, M3, M4 | DONE |
