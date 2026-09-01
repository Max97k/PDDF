# DISPATCH — Milestone 1 Explorer 1

## Identity
- Role: M1 Architecture Explorer (MainActivity & Component Extraction)
- Working Directory: c:\Users\b\PDDF\.agents\m1_explorer_1
- Parent Conversation ID: 408f3427-07df-48e6-a3ce-0638f3e78ce2

## References
- ORIGINAL_REQUEST: c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md
- Guidelines: c:\Users\b\PDDF\AGENTS.md
- Project Spec: c:\Users\b\PDDF\PROJECT.md

## Scope (Milestone 1: Architecture & UI Modularization)
Analyze the exact refactoring plan for `MainActivity.kt` and component decomposition:
1. Extraction of `SelectedFilesCard`, `PasswordInputSection`, `DocumentDetailsCard`, `EmptyStateCard`, `ThemeDropdownMenu`, `WhatsNewDialog` into `app/src/main/java/com/example/ui/components/`.
2. Extraction of `SavedPasswordListDialog`, `SavePasswordDialog`, `BiometricHelper` into `app/src/main/java/com/example/feature/vault/`.
3. Extraction of `PdfViewerDialog`, `PdfViewerScreen`, `PdfPageItem` into `app/src/main/java/com/example/feature/viewer/` with Dispatchers.IO isolation.
4. Extraction of `PDFDecryptorScreen`, `BatchProgressDialog`, `AutoUnlockPasswordDialog` into `app/src/main/java/com/example/feature/decrypt/`.
5. Keeping `MainActivity.kt` slim (~100 lines) handling only activity lifecycle, intent routing, and root Scaffold.

Provide exact file boundaries, import maps, and write your recommendations to `c:\Users\b\PDDF\.agents\m1_explorer_1\handoff.md`.
