# DISPATCH — Milestone 1 Explorer 2

## Identity
- Role: M1 UDF & Stateflow Explorer (MainUiState & UiEffect Architecture)
- Working Directory: c:\Users\b\PDDF\.agents\m1_explorer_2
- Parent Conversation ID: 408f3427-07df-48e6-a3ce-0638f3e78ce2

## References
- ORIGINAL_REQUEST: c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md
- Guidelines: c:\Users\b\PDDF\AGENTS.md
- Project Spec: c:\Users\b\PDDF\PROJECT.md

## Scope (Milestone 1: Architecture & UI Modularization)
Analyze the exact design for `MainUiState`, `UiEffect`, and `MainViewModel.kt`:
1. Design `MainUiState.kt` (single immutable data class with `@Immutable` annotations, encapsulating selected files, metadata, processing status, batch state, dialog visibility states, theme mode, preview uri, etc.).
2. Design `UiEffect.kt` (sealed interface for single-shot events: `ShowSnackbar`, `LaunchFilePicker`, `LaunchDirectoryPicker`, `TriggerBiometric`, etc.).
3. Refactor `MainViewModel.kt` to eliminate 20+ disparate MutableStateFlows, replacing them with `_uiState: MutableStateFlow<MainUiState>` and `_uiEffect: Channel<UiEffect>`.
4. Ensure clean interaction between ViewModel and UseCases (`DecryptPdfUseCase`, `AutoUnlockUseCase`, `BatchProcessUseCase`, `PasswordVaultUseCase`).

Write your recommendations, data models, and event channels to `c:\Users\b\PDDF\.agents\m1_explorer_2\handoff.md`.
