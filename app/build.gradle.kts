import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.hilt)
  id("jacoco")
}

jacoco {
  toolVersion = "0.8.12"
}

android {
  namespace = "com.max97k.pddf"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.max97k.pddf"
    minSdk = 36
    targetSdk = 37
    versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 19
    versionName = (project.findProperty("versionName") as? String) ?: "0.9.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  androidResources {
    generateLocaleConfig = true
    localeFilters += listOf("zh-rTW", "zh-rCN", "ja", "es", "de", "fr", "en")
  }

  signingConfigs {
    create("release") {
      val keystoreFile = file("${layout.buildDirectory.get()}/release.keystore")
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystore = file("${layout.buildDirectory.get()}/release.keystore")
      if (releaseKeystore.exists()) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
      ndk {
        debugSymbolLevel = "FULL"
      }
    }
    debug {
      enableUnitTestCoverage = false
    }
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      useLegacyPackaging = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
      all {
        it.jvmArgs(
          "-Drobolectric.dependency.repo.url=https://repo1.maven.org/maven2/",
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--enable-native-access=ALL-UNNAMED"
        )
      }
    }
  }
  lint {
    abortOnError = true
    checkReleaseBuilds = true
  }
}

tasks.withType<Test>().configureEach {
  extensions.findByType(JacocoTaskExtension::class.java)?.apply {
    isIncludeNoLocationClasses = false
    excludes = listOf("jdk.internal.*", "sun.*", "java.*", "javax.*", "org.conscrypt.*")
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  val kotlinClasses = fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
    exclude(
      "**/R.class",
      "**/R$*.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Test*.*",
      "android/**/*"
    )
  }
  val javaClasses = fileTree("${project.layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
    exclude(
      "**/R.class",
      "**/R$*.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Test*.*",
      "android/**/*"
    )
  }
  val mainSrc = "${project.projectDir}/src/main/java"

  sourceDirectories.setFrom(files(mainSrc))
  classDirectories.setFrom(files(kotlinClasses, javaClasses))
  executionData.setFrom(fileTree(project.layout.buildDirectory.get()).matching {
    include("jacoco/testDebugUnitTest.exec", "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
  })
}

tasks.withType<Test>().configureEach {
  (extensions.findByName("jacoco") as? org.gradle.testing.jacoco.plugins.JacocoTaskExtension)?.apply {
    isEnabled = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.adaptive.navigation)
  implementation(libs.androidx.compose.material3.adaptive.layout)
  implementation(libs.androidx.compose.material3.windowsizeclass)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.startup)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.pdfbox.android)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.pdf.viewer.fragment)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.google.app.update)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.hilt.work)
  "ksp"(libs.hilt.compiler)
  "ksp"(libs.androidx.hilt.compiler)
  "ksp"(libs.androidx.room.compiler)
}
