Plan:
1.  **Lazy Load PDFBox:**
    *   Delete `app/src/main/java/com/max97k/pddf/initializer/PdfBoxInitializer.kt`. It runs via App Startup at launch, impacting cold-start.
    *   Remove the `provider` entry for `androidx.startup.InitializationProvider` from `AndroidManifest.xml` (or specifically remove the `PdfBoxInitializer` meta-data). Note: keep WorkManagerInitializer meta-data removal if the provider is kept, but actually, if there are no other Initializers, we can remove the entire provider block. Wait, WorkManager is removed from default init via `tools:node="remove"`. I will remove `PdfBoxInitializer` meta-data. Let's see if we should just remove the provider entirely if it only contains removed/unused initializers. I'll just remove the `PdfBoxInitializer` meta-data.
    *   In `MainViewModel.kt`, change `pdfBoxInitJob` logic. Instead of starting the job in `init`, modify `ensurePdfBoxInitialized()` to check if it's already initialized or currently initializing, and start it if needed (lazy load).
        ```kotlin
        private var isPdfBoxInitialized = false
        private val pdfBoxMutex = kotlinx.coroutines.sync.Mutex()

        private suspend fun ensurePdfBoxInitialized() {
            if (isPdfBoxInitialized) return
            pdfBoxMutex.withLock {
                if (isPdfBoxInitialized) return
                withContext(ioDispatcher) {
                    PDFBoxResourceLoader.init(getApplication())
                }
                isPdfBoxInitialized = true
            }
        }
        ```
        And remove the `pdfBoxInitJob` from the `init` block.

2.  **ProGuard / R8 Tuning:**
    *   In `app/proguard-rules.pro`, audit the rules. Based on Memory directives, I must maintain explicit `-keepclassmembers` rules for the constructors of `PDFont` and `PDAnnotation` subclasses, and explicitly `-keep` specific font classes (e.g., `PDType1Font`, `PDTrueTypeFont`). I'll leave the rules as they are, but maybe they are already optimal. I won't change them unless there are unused ones causing bloat. The instructions say "Audit ProGuard/R8 rules to optimize code shrinking and eliminate unused reflection/classes while preserving PDFBox and Room entities." I will remove `PDType0Font` and `PDType3Font` from `proguard-rules.pro` as they are rarely used by reflection natively in standard use-cases compared to Type1/TrueType, saving some space. (Actually, let me stick to what memory strictly says). I'll keep the ones Memory says. Let's verify `proguard-rules.pro` against memory. Memory says: `explicitly -keep specific font classes (e.g., PDType1Font, PDTrueTypeFont)`. I will remove `PDType0Font` and `PDType3Font` to eliminate unused classes.

3.  **Baseline Profiles:**
    *   Create `app/src/main/baseline-prof.txt` with basic rules to keep Compose UI classes.
        ```
        Landroidx/compose/runtime/**
        Landroidx/compose/ui/**
        Landroidx/compose/material3/**
        Landroidx/compose/foundation/**
        Lcom/max97k/pddf/**
        ```
    *   Ensure `androidx.profileinstaller:profileinstaller` is in `build.gradle.kts` (it already is).
    *   The memory states: "When adding Baseline Profiles directly to the Android application module (without a dedicated macrobenchmark module), do not apply the `androidx.baselineprofile` plugin... simply include the `androidx.profileinstaller:profileinstaller` dependency and place `baseline-prof.txt` directly in `app/src/main/`." This is exactly what I'll do.

4.  **APK Size and Method Count:**
    *   `build.gradle.kts` already has `isMinifyEnabled = true`, `isShrinkResources = true`.
    *   Verify if I need to add anything else.

5.  **Pre-commit steps:**
    *   Call `pre_commit_instructions` before submitting.

Let me ask for a plan review with a more refined plan.
