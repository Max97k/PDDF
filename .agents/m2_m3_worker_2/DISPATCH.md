## 2026-09-01T05:45:48Z
You are Worker 2 for Max97k/PDDF Modernization (Milestone 2 & Milestone 3 Implementation).
Your Working Directory: c:\Users\b\PDDF\.agents\m2_m3_worker_2

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Context & Reference Files:
- c:\Users\b\PDDF\.agents\ORIGINAL_REQUEST.md
- c:\Users\b\PDDF\AGENTS.md
- c:\Users\b\PDDF\PROJECT.md
- c:\Users\b\PDDF\.agents\explorer_gen2_2\handoff.md

Your Instructions:
1. **Dependencies & 16KB Packaging** (`gradle/libs.versions.toml` & `app/build.gradle.kts`):
   - Add `androidx.work:work-runtime-ktx:2.9.1` to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
   - In `app/build.gradle.kts`, set `packaging { jniLibs { useLegacyPackaging = false } }` for Android 15 16KB ELF page size compliance.
2. **Hardware Security (M2)**:
   - `app/src/main/java/com/example/util/CryptoManager.kt`:
     - Detect StrongBox support (`context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)`).
     - When building KeyGenParameterSpec, set `.setKeySize(256)` and attempt `.setIsStrongBoxBacked(true)` with graceful fallback to standard TEE KeyStore if StrongBox is unsupported/fails (e.g. in emulator/Robolectric).
     - Implement `initCipherForBiometric(mode: Int): Cipher` (returns Cipher initialized for ENCRYPT_MODE or DECRYPT_MODE).
   - `app/src/main/java/com/example/feature/vault/BiometricHelper.kt`:
     - Implement `authenticateWithCrypto(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String, onAuthenticated: (Cipher) -> Unit, onError: (Int, String) -> Unit)` wrapping `BiometricPrompt.CryptoObject(cipher)`.
   - `app/src/main/java/com/example/util/FileUtils.kt`:
     - Upgrade `secureDelete(file: File?): Boolean` to strictly implement the **DoD 5220.22-M 3-pass overwrite standard**:
       - Pass 1: Overwrite entire file with `0x00` (zero bytes)
       - Pass 2: Overwrite entire file with `0xFF` (all-ones bytes)
       - Pass 3: Overwrite entire file with cryptographic pseudo-random bytes (`SecureRandom`)
       - After each pass (or final pass), flush hardware storage buffer with `raf.fd.sync()` before `file.delete()`.
   - `app/src/main/java/com/example/util/MemoryUtils.kt`:
     - Ensure `wipe(CharArray)` and `wipe(ByteArray)` are properly callable and integrate memory zeroization when clearing passwords or on timeout in `MainViewModel`.
   - `app/src/main/java/com/example/data/worker/BatchDecryptWorker.kt`:
     - Create `BatchDecryptWorker` extending `CoroutineWorker`.
     - Implement background batch decryption on `Dispatchers.IO`, updating progress via `setProgressAsync`, maintaining an ongoing foreground notification with progress bar, and supporting cancellation.
   - `app/src/main/java/com/example/feature/decrypt/PDFDecryptorScreen.kt`:
     - Ensure `isSecureModeActive` also includes `showAutoUnlockPasswordPrompt` so `FLAG_SECURE` is applied when auto-unlock password dialog is visible.
3. **Adaptive Form Factors & Predictive Back (M3)**:
   - `app/src/main/java/com/example/feature/decrypt/PDFDecryptorScreen.kt`:
     - Implement `BackHandler` in Compose to clear selected files or dismiss dialogs on back gesture.
     - Implement adaptive tablet layout: when `windowWidthSizeClass == WindowWidthSizeClass.Expanded` (or wide screens), render a side-by-side dual-pane layout (Left Pane: Selected files & Document Details; Right Pane: Password input section, decrypt actions, and preview). When `Compact`, render the single-column layout.
   - `app/src/main/java/com/example/ui/components/PasswordInputSection.kt`, `AutoUnlockPasswordDialog.kt`, `SavePasswordDialog.kt`:
     - Add `Modifier.imePadding()` to ensure the UI shifts gracefully above the software keyboard.
4. **Verification**:
   - Run tests: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`
   - Verify code compiles cleanly with 0 compilation errors and unit tests pass.
   - Write comprehensive report to `c:\Users\b\PDDF\.agents\m2_m3_worker_2\handoff.md` and send completion message to parent.
