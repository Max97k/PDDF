# Handoff Report — Explorer 2: Milestone 2 & Milestone 3 Audit

**Timestamp**: 2026-09-01T05:32:00Z  
**Author**: Explorer 2 (Security & Adaptive UI Auditor)  
**Assigned Scope**: 
- Milestone 2: Hardware Security & Background Processing (Patches 13–24)
- Milestone 3: Android 15 & Adaptive Form Factors (Patches 25–35)

---

## 1. Observation

### Milestone 2: Hardware Security & Background Processing

#### 1.1 `CryptoManager.kt` & Keystore Encryption
- **File**: `app/src/main/java/com/example/util/CryptoManager.kt`
- **Lines 45–58**:
  ```kotlin
  private fun generateKey(): SecretKey {
      val keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
      val keyGenParameterSpec = KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
          .setBlockModes(BLOCK_MODE)
          .setEncryptionPaddings(PADDING)
          .setRandomizedEncryptionRequired(true)
          .build()
      
      keyGenerator.init(keyGenParameterSpec)
      return keyGenerator.generateKey()
  }
  ```
- **Finding**:
  1. StrongBox hardware security module detection (`PackageManager.FEATURE_STRONGBOX_KEYSTORE` / `.setIsStrongBoxBacked(true)` with TEE fallback) is **not implemented**.
  2. Key size is not explicitly configured to 256 bits (`.setKeySize(256)`).
  3. No `initCipherForBiometric(mode: Int): Cipher` or `CryptoObject` cipher initialization is exposed for biometric authentication.

#### 1.2 `BiometricHelper.kt`
- **File**: `app/src/main/java/com/example/feature/vault/BiometricHelper.kt`
- **Lines 14–57**:
  ```kotlin
  object BiometricHelper {
      fun canAuthenticate(context: Context): Boolean {
          val biometricManager = BiometricManager.from(context)
          return biometricManager.canAuthenticate(
              BIOMETRIC_STRONG or DEVICE_CREDENTIAL
          ) == BiometricManager.BIOMETRIC_SUCCESS
      }
      fun authenticate(
          activity: FragmentActivity,
          ...
      ) { ...
          val promptInfo = BiometricPrompt.PromptInfo.Builder()
              .setTitle(title)
              .setSubtitle(subtitle)
              .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
              .build()
          biometricPrompt.authenticate(promptInfo)
      }
  }
  ```
- **Finding**:
  1. `BiometricPrompt.authenticate(PromptInfo, CryptoObject)` is **not implemented**. No `CryptoObject` or `authenticateWithCrypto` method exists.
  2. Combining `DEVICE_CREDENTIAL` with `CryptoObject` requires explicit handling (Android Keystore auth-per-use keys require `BIOMETRIC_STRONG` on API < 30).

#### 1.3 `FileUtils.kt` — DoD 5220.22-M File Shredding
- **File**: `app/src/main/java/com/example/util/FileUtils.kt`
- **Lines 45–65**:
  ```kotlin
  fun secureDelete(file: File?): Boolean {
      if (file == null || !file.exists()) return true
      return try {
          if (file.isFile && file.length() > 0) {
              val length = file.length()
              RandomAccessFile(file, "rws").use { raf ->
                  val buffer = ByteArray(4096.coerceAtMost(length.toInt()).coerceAtLeast(1))
                  SecureRandom().nextBytes(buffer)
                  var written = 0L
                  while (written < length) {
                      val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                      raf.write(buffer, 0, toWrite)
                      written += toWrite
                  }
              }
          }
          file.delete()
      } catch (_: Exception) {
          file.delete()
      }
  }
  ```
- **Finding**:
  1. Only single-pass overwrite with random bytes is performed.
  2. Does **not** implement the DoD 5220.22-M 3-pass specification (Pass 1: 0x00 zero bytes, Pass 2: 0xFF all-ones, Pass 3: cryptographic pseudo-random bytes + `raf.fd.sync()`).

#### 1.4 `MemoryUtils.kt` — Sensitive Memory Zeroization
- **File**: `app/src/main/java/com/example/util/MemoryUtils.kt`
- **Lines 1–12**:
  ```kotlin
  package com.example.util
  object MemoryUtils {
      fun wipe(chars: CharArray) { chars.fill('\u0000') }
      fun wipe(bytes: ByteArray) { bytes.fill(0) }
  }
  ```
- **Finding**:
  - `grep_search` for `MemoryUtils` across the entire codebase revealed **0 call sites outside its declaration**.
  - All password state in `MainUiState`, `MainViewModel`, `AutoUnlockPasswordDialog`, `SavePasswordDialog`, and `PasswordRepository` is handled as immutable `String` instances without zeroization on dispose/timeout.

#### 1.5 `BatchDecryptWorker.kt` & WorkManager Background Decryption
- **File Check**: `com/example/data/worker/BatchDecryptWorker.kt` — **File Does Not Exist**.
- **Dependencies (`gradle/libs.versions.toml` & `app/build.gradle.kts`)**: `androidx.work:work-runtime-ktx` is **not declared**.
- **Finding**:
  - Batch decryption currently runs inside `MainViewModel.batchJob` Coroutine via `BatchProcessUseCase`.
  - Background execution, WorkManager queuing (`OneTimeWorkRequestBuilder`), foreground notifications with ongoing progress bar, and system notification cancellation are **not implemented**.

#### 1.6 Security Safeguards: Auto-Clear Timeout, FLAG_SECURE, Cloud Backup Exclusion
- **Auto-Clear Password Timeout**:
  - `MainViewModel.kt` lines 776–785: Implements 60-second elapsed check on `onAppForegrounded()`.
  - Gap: No active foreground inactivity timer; password string is simply reassigned to `""` without buffer wiping.
- **FLAG_SECURE**:
  - `PDFDecryptorScreen.kt` lines 167–178: Applies `WindowManager.LayoutParams.FLAG_SECURE` when `uiState.isSecureModeActive` (`showPasswordListDialog || showSavePasswordDialog`).
  - Gap: `showAutoUnlockPasswordPrompt` is not included in `isSecureModeActive`.
- **Cloud Backup Exclusion**:
  - `AndroidManifest.xml` lines 7–9: `android:allowBackup="false"`, `dataExtractionRules="@xml/data_extraction_rules"`, `fullBackupContent="@xml/backup_rules"`.
  - `res/xml/backup_rules.xml` & `res/xml/data_extraction_rules.xml`: Properly exclude database `pdf-decryptor-db` and SharedPreferences `pdf_decryptor_prefs.xml`.

---

### Milestone 3: Android 15 & Adaptive Form Factors

#### 2.1 16KB Page Size Compliance & Target SDK 35
- **File**: `app/build.gradle.kts`
- **Lines 19, 24, 68–70**:
  ```kotlin
  compileSdk = 35
  defaultConfig {
      targetSdk = 35
  }
  packaging {
      jniLibs {
          useLegacyPackaging = true
      }
  }
  ```
- **Finding**:
  - `targetSdk = 35` and `compileSdk = 35` are properly configured.
  - `useLegacyPackaging = true` violates 16KB ELF page size compliance. Android 15 16KB alignment requires `useLegacyPackaging = false` so that native shared libraries (`.so`) remain uncompressed and 16KB-page-aligned within the APK.

#### 2.2 Predictive Back Gesture Handling
- **File**: `app/src/main/AndroidManifest.xml` line 6: `android:enableOnBackInvokedCallback="true"`.
- **Finding**:
  - Manifest attribute is set.
  - However, in Jetpack Compose, no `BackHandler` or `PredictiveBackHandler` is attached to `PDFDecryptorScreen`, `PdfViewerScreen`, or dialogs to handle back gesture animations or dismissals gracefully.

#### 2.3 Edge-to-Edge Window Insets & IME Keyboard Padding
- **File**: `app/src/main/java/com/example/MainActivity.kt` line 34: `enableEdgeToEdge()` is called.
- **File**: `app/src/main/java/com/example/feature/viewer/PdfViewerScreen.kt` lines 186, 325: Uses `WindowInsets.safeDrawing` and `navigationBarsPadding()`.
- **Finding**:
  - `Modifier.imePadding()` is **missing** across all input sections (`PasswordInputSection.kt`, `AutoUnlockPasswordDialog.kt`, `SavePasswordDialog.kt`), causing software keyboard occlusion on smaller screens.

#### 2.4 Tablet Dual-Pane & Foldable Tabletop Posture
- **File**: `app/src/main/java/com/example/MainActivity.kt` line 37: `calculateWindowSizeClass(this)` computes `windowSizeClass.widthSizeClass`.
- **File**: `app/src/main/java/com/example/feature/decrypt/PDFDecryptorScreen.kt` lines 96, 141: Accepts `windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact`.
- **Finding**:
  1. `PDFDecryptorScreen` ignores `windowWidthSizeClass` entirely and only renders a single centered `Column` layout. No dual-pane master-detail layout exists for `WindowWidthSizeClass.Expanded` (Tablets / Foldables unfolded).
  2. No foldable posture detection (`FoldingFeature`, `FoldingFeature.State.HALF_OPENED`, tabletop mode). `androidx.window:window` dependency is **missing**.

#### 2.5 Drag-and-Drop Ingestion, AMOLED Theme, Haptics
- **Drag-and-Drop PDF Ingestion**: `PDFDecryptorScreen.kt` lines 236–264, 287–295 implement `Modifier.dragAndDropTarget` checking MIME `application/pdf` with visual highlight and `requestDragAndDropPermissions`. **(Implemented)**
- **AMOLED Pure Black Theme**: `ThemePreferences.kt`, `ThemeDropdownMenu.kt`, and `Theme.kt` define `AmoledColorScheme` with `Color.Black` background and surface. **(Implemented)**
- **Rich Contextual Haptics**: Implemented across buttons, list items, and status changes using `LocalHapticFeedback` (`TextHandleMove`, `LongPress`) and `LocalView` (`HapticFeedbackConstants.CONFIRM`, `REJECT`, `KEYBOARD_TAP`). **(Implemented)**

---

## 2. Logic Chain

1. **Hardware Keystore & Biometric Authentication**:
   - `CryptoManager.generateKey()` uses basic `KeyGenParameterSpec` without testing for `FEATURE_STRONGBOX_KEYSTORE` or enabling StrongBox. If StrongBox is available on the device (e.g. Pixel 3+ / Titan M / modern SoCs), the app fails to utilize the dedicated HSM chip.
   - `BiometricHelper.authenticate()` does not take a `Cipher` or `CryptoObject`. Without a `CryptoObject`, biometric authentication is merely a UI gate rather than cryptographic decryption of sensitive keys.
2. **File Shredding & Memory Security**:
   - DoD 5220.22-M standard mandates 3 overwrites (0x00, 0xFF, pseudo-random) + hardware cache flush (`fsync`). `FileUtils.secureDelete` only does 1 pass of random data, leaving potential remnants on flash storage.
   - `MemoryUtils.wipe()` exists but is never invoked because passwords are kept in immutable `String` fields. A heap dump or memory inspection could reveal sensitive passwords.
3. **Background Processing**:
   - For batch decryption of 10+ PDFs, process death while backgrounded will kill the coroutine in `MainViewModel`. WorkManager is needed for guaranteed execution and system tray progress notifications.
4. **Android 15 & Form Factors**:
   - Setting `useLegacyPackaging = true` causes Gradle to compress `.so` files, violating Android 15's 16KB page boundary alignment on 64-bit ARM architectures.
   - Without `windowWidthSizeClass` branching and `androidx.window` posture detection, large screen tablets and foldable devices render a stretched, suboptimal single column.

---

## 3. Caveats

- **No Caveats on Codebase Inspection**: Full source code, manifests, Gradle configs, resource files, and tests were directly viewed and verified.
- **Test Execution Environment**: When executing JVM unit tests via Gradle CLI under Windows with Android Studio JBR, JaCoCo `0.8.12` encountered `IllegalArgumentException: Unsupported class file major version 69` on system classes due to Java version compatibility; pure unit tests without JaCoCo agent instrumentation run cleanly.

---

## 4. Conclusion & Action Plan

### Milestone 2 Status: PARTIALLY IMPLEMENTED (Significant Gaps)
- **Implemented**: Room AES-GCM-256 password encryption, Cloud backup exclusion XMLs, basic app background timeout tracking, basic single-pass file delete.
- **Missing / Action Steps**:
  1. **StrongBox Detection**: In `CryptoManager.kt`, check `context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)` and initialize `KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)` with fallback to standard TEE.
  2. **Biometric CryptoObject**: Add `CryptoManager.initCipherForBiometric(mode: Int): Cipher` and `BiometricHelper.authenticateWithCrypto(...)` with `BiometricPrompt.CryptoObject(cipher)`.
  3. **DoD 5220.22-M 3-Pass Shredding**: Update `FileUtils.secureDelete` to execute Pass 1 (0x00), Pass 2 (0xFF), Pass 3 (random) and call `raf.fd.sync()`.
  4. **Password Zeroization**: Wire `MemoryUtils.wipe` into password flows, dialog dismissals, and timeout resets.
  5. **WorkManager Batch Worker**: Add `androidx.work:work-runtime-ktx` to dependencies, implement `BatchDecryptWorker` with ongoing progress notification (`ForegroundInfo`), and wire into `MainViewModel` for batch operations.
  6. **FLAG_SECURE**: Extend `isSecureModeActive` to cover `showAutoUnlockPasswordPrompt`.

### Milestone 3 Status: PARTIALLY IMPLEMENTED (Key Adaptive Gaps)
- **Implemented**: `compileSdk=35` & `targetSdk=35`, AMOLED Pure Black theme & DataStore preferences, Drag-and-Drop PDF ingestion, Contextual Haptic feedback.
- **Missing / Action Steps**:
  1. **16KB ELF Page Compliance**: In `app/build.gradle.kts`, change `jniLibs { useLegacyPackaging = true }` to `jniLibs { useLegacyPackaging = false }`.
  2. **Predictive Back in Compose**: Add `BackHandler` in `PDFDecryptorScreen` (when files are selected), `PdfViewerScreen`, and dialogs.
  3. **IME Padding**: Add `Modifier.imePadding()` to `PasswordInputSection.kt`, `AutoUnlockPasswordDialog.kt`, and `SavePasswordDialog.kt`.
  4. **Tablet Dual-Pane**: Update `PDFDecryptorScreen` to render side-by-side panes when `windowWidthSizeClass == WindowWidthSizeClass.Expanded` (left pane: file selection & metadata; right pane: password input, actions, preview).
  5. **Foldable Posture Support**: Add `androidx.window:window` and adapt layout for tabletop half-folded posture.

---

## 5. Verification Method

1. **StrongBox & Keystore Verification**:
   - Inspect `CryptoManager.kt` for `FEATURE_STRONGBOX_KEYSTORE` check, `setIsStrongBoxBacked(true)`, and `initCipherForBiometric`.
2. **File Shredding Verification**:
   - Run `FileUtilsTest.kt` with assertions validating 3-pass overwrite patterns (0x00, 0xFF, random) before deletion.
3. **16KB Page Size Verification**:
   - Verify `app/build.gradle.kts` contains `jniLibs { useLegacyPackaging = false }`.
4. **WorkManager & Background Worker Verification**:
   - Inspect `BatchDecryptWorker.kt` under `com.example.data.worker`, check notification channel creation, `setProgressAsync`, and `setForegroundAsync`.
5. **Adaptive Layout Verification**:
   - Run Compose UI / Roborazzi screenshot tests on Tablet (`Expanded`) and Phone (`Compact`) configurations to verify dual-pane and single-pane rendering.
