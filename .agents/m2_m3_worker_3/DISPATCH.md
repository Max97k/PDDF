## 2026-09-01T06:00:11Z
Assignment: Milestone 2 & Milestone 3 Direct Implementation (Worker 3)
Tasks:
1. `app/build.gradle.kts` & `gradle/libs.versions.toml`:
   - Add `androidx.work:work-runtime-ktx:2.9.1` to dependencies.
   - In `app/build.gradle.kts`, set `packaging { jniLibs { useLegacyPackaging = false } }` for Android 15 16KB ELF page size compliance.
2. `app/src/main/java/com/example/util/CryptoManager.kt`:
   - Detect StrongBox support (`context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)`).
   - Set `.setKeySize(256)` and attempt `.setIsStrongBoxBacked(true)` with fallback to standard TEE.
   - Add `initCipherForBiometric(mode: Int): Cipher` (returns Cipher initialized for ENCRYPT_MODE or DECRYPT_MODE).
3. `app/src/main/java/com/example/feature/vault/BiometricHelper.kt`:
   - Add `authenticateWithCrypto(activity: FragmentActivity, cipher: Cipher, title: String, subtitle: String, onAuthenticated: (Cipher) -> Unit, onError: (Int, String) -> Unit)` wrapping `BiometricPrompt.CryptoObject(cipher)`.
4. `app/src/main/java/com/example/util/FileUtils.kt`:
   - Upgrade `secureDelete(file: File?): Boolean` to strictly implement the DoD 5220.22-M 3-pass overwrite standard (Pass 1: 0x00, Pass 2: 0xFF, Pass 3: SecureRandom bytes + `raf.fd.sync()`).
5. `app/src/main/java/com/example/util/MemoryUtils.kt`:
   - Ensure `wipe(CharArray)` and `wipe(ByteArray)` are properly callable.
6. `app/src/main/java/com/example/data/worker/BatchDecryptWorker.kt`:
   - Create `BatchDecryptWorker` extending `CoroutineWorker` for background batch decryption on `Dispatchers.IO` with ongoing foreground notification and progress updates (`setProgressAsync`).
7. `app/src/main/java/com/example/feature/decrypt/PDFDecryptorScreen.kt`:
   - Include `showAutoUnlockPasswordPrompt` in `isSecureModeActive` (for `FLAG_SECURE`).
   - Add `BackHandler` in Compose to clear selected files or dismiss dialogs on back gesture.
   - Add adaptive dual-pane layout when `windowWidthSizeClass == WindowWidthSizeClass.Expanded` (Left Pane: Files & Document details; Right Pane: Password input, decrypt actions, preview).
8. `app/src/main/java/com/example/ui/components/PasswordInputSection.kt`, `AutoUnlockPasswordDialog.kt`, `SavePasswordDialog.kt`:
   - Add `Modifier.imePadding()` for soft keyboard avoidance.
9. Verification:
   - Run: `powershell` with `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest`
   - Ensure all unit tests pass with 0 failures.
   - Write handoff report and notify parent.
