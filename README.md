# PDF Decryptor (Android App)

An Android application built with **Kotlin** and **Jetpack Compose** that enables users to easily batch decrypt password-protected PDF documents, manage saved password profiles locally with Room Database, and customize file conflict and prefix preferences.

---

## 🌟 Key Features

- 📄 **Batch PDF Selection & Decryption**: Process multiple encrypted PDF files at once with asynchronous I/O offloading for smooth UI performance.
- 📲 **Seamless Android Intent Integration**: Open encrypted PDFs directly from File Managers, WhatsApp, Email, or Web Browsers via `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` intent handling.
- 🔑 **Local Password Vault**: Safely store and auto-fill frequently used PDF passwords using an encrypted Room database DAO setup.
- ⚙️ **Custom Output & File Management**:
  - Choose between **Save as Copy** (via SAF `ACTION_CREATE_DOCUMENT`) or **Overwrite** (in-place) when handling files.
  - Quick action buttons to open the system File Manager or preview decrypted files with an external PDF viewer.
- 🎨 **Modern Material 3 Design & Localized UI**: Fully responsive Jetpack Compose interface with support for Traditional Chinese (`zh-rTW`) and English (`en`).
- 🛡️ **Enhanced Security & Data Backup**: Configured `data_extraction_rules.xml` and `backup_rules.xml` to exclude sensitive database and preference stores from cloud backup or device transfers.

---

## 🏗️ Architecture & Tech Stack

- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous & Flow**: Kotlin Coroutines & `StateFlow` / `collectAsStateWithLifecycle`
- **Database**: Room Persistence Library (`2.7.0`) with KSP
- **PDF Engine**: Apache PDFBox Android (`2.0.27.0`)
- **Build System**: Android Gradle Plugin `9.1.1`, Kotlin `2.2.10`, KSP `2.3.5`
- **Testing & Quality Assurance**:
  - **Unit Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric
  - **Code Coverage**: JaCoCo (Target > 70% coverage on ViewModel & Repository layer)
  - **Screenshot Verification**: Roborazzi

---

## ⚡ APK / App Bundle Size Optimization Strategy

To ensure minimal download size while preserving PDF decryption accuracy and font rendering integrity:
1. **AAB (Android App Bundle) Distribution**: Distribute via AAB on Google Play for dynamic ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and density splitting.
2. **R8 Full Mode & Shrinking**: Minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) with R8 Full Mode enabled.
3. **Locale Filtering**: Resource filtering configured for `zh-rTW` and `en` (`localeFilters += listOf("zh-rTW", "en")`) to purge unused library localization assets.
4. **Preserved PDF Engine**: Full retention of PDFBox CMap & Font mapping to guarantee zero乱码 (garbage text) and 100% decryption compatibility.

---

## 🧪 Testing & Code Coverage

### Running Unit Tests
To run all unit tests locally on JVM:
```bash
./gradlew :app:testDebugUnitTest
```

### Generating JaCoCo Coverage Report
To run unit tests and generate the HTML/XML test coverage reports:
```bash
./gradlew :app:jacocoTestReport
```
The resulting coverage reports can be found in `app/build/reports/jacoco/jacocoTestReport/`.

---

## 📁 Project Structure

```
app/src/
├── main/
│   ├── java/com/example/
│   │   ├── MainActivity.kt         # Compose UI entry point & Intent handling
│   │   ├── MainViewModel.kt        # State Management & Decryption Business Logic
│   │   ├── data/                   # Room Database, Entity & Repository
│   │   │   ├── AppDatabase.kt
│   │   │   ├── PasswordDao.kt
│   │   │   ├── PasswordEntity.kt
│   │   │   └── PasswordRepository.kt
│   │   ├── ui/theme/               # Material 3 Design System & Theme
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   └── util/
│   │       └── FileUtils.kt        # Uri resolution & File IO helpers
│   └── res/
│       ├── values/                 # Base strings & themes
│       ├── values-zh-rTW/          # Traditional Chinese translations
│       └── xml/                    # Security & backup rules
└── test/
    └── java/com/example/           # Unit tests for ViewModel, Repository & FileUtils
```

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.

