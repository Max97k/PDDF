# PDF Decryptor (Android App)

An Android application built with **Kotlin** and **Jetpack Compose** that enables users to easily batch decrypt password-protected PDF documents, manage saved password profiles locally with Room Database, and customize file conflict and prefix preferences.

---

## 🌟 Key Features

- 📄 **Batch PDF Selection & Decryption**: Process multiple encrypted PDF files at once with asynchronous I/O offloading for smooth UI performance.
- 🔑 **Local Password Vault**: Safely store and auto-fill frequently used PDF passwords using an encrypted Room database DAO setup.
- ⚙️ **Custom Output Options**:
  - Add custom prefixes to decrypted output files.
  - Choose between **Save as Copy** or **Overwrite** when naming collisions occur.
  - Option to automatically delete original encrypted files after successful decryption.
- 🎨 **Modern Material 3 Design**: Fully responsive Jetpack Compose interface supporting dynamic color schemes and accessibility standards.
- 🛡️ **Enhanced Security & Data Backup**: Configured `data_extraction_rules.xml` and `backup_rules.xml` to exclude sensitive database and preference stores from cloud backup or device transfers.

---

## 🏗️ Architecture & Tech Stack

- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM (Model-View-ViewModel) + Clean Architecture principles
- **Asynchronous & Flow**: Kotlin Coroutines & `StateFlow` / `collectAsStateWithLifecycle`
- **Database**: Room Persistence Library (KSP)
- **PDF Engine**: Apache PDFBox Android
- **Testing & Quality Assurance**:
  - **Unit Testing**: JUnit 4, Kotlin Coroutines Test, Robolectric
  - **Code Coverage**: JaCoCo (Target > 70% coverage on ViewModel & Repository layer)
  - **Screenshot Verification**: Roborazzi

---

## 🧪 Testing & Code Coverage

### Running Unit Tests
To run all unit tests locally on JVM:
```bash
gradle :app:testDebugUnitTest
```

### Generating JaCoCo Coverage Report
To run unit tests and generate the HTML/XML test coverage reports:
```bash
gradle :app:jacocoTestReport
```
The resulting coverage reports can be found in `app/build/reports/jacoco/jacocoTestReport/`.

---

## 📁 Project Structure

```
app/src/
├── main/
│   ├── java/com/example/
│   │   ├── MainActivity.kt         # Compose UI entry point & Screen layout
│   │   ├── MainViewModel.kt        # State Management & Decryption Business Logic
│   │   ├── data/                   # Room Database, Entity & Repository
│   │   │   ├── AppDatabase.kt
│   │   │   ├── PasswordDao.kt
│   │   │   ├── PasswordEntity.kt
│   │   │   └── PasswordRepository.kt
│   │   └── util/
│   │       └── FileUtils.kt        # File name resolution helpers
│   └── res/xml/                    # Security & backup rules
└── test/
    └── java/com/example/           # Unit tests for ViewModel, Repository & FileUtils
```

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.
