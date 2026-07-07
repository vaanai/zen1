plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// CI injects the run number so every published build carries a distinct, monotonically
// increasing versionCode — otherwise sideloading a new APK is a same-version reinstall
// and there is no way to tell builds apart on the phone.
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

android {
    namespace = "com.example.zen"
    compileSdk = 36
    defaultConfig {
        // Distinct from the source `namespace` (com.example.zen) — Google Play rejects com.example.* ids.
        applicationId = "com.zenblocker.app"
        minSdk = 24
        targetSdk = 36
        // +100 keeps CI codes above the historically shipped versionCode 2 and local builds.
        versionCode = ciRunNumber?.plus(100) ?: 3
        versionName = ciRunNumber?.let { "1.2.$it" } ?: "1.2.0-local"
    }

    signingConfigs {
        // A fixed keystore committed to the repo so every build — local or CI — is signed with the
        // SAME key. Without this, GitHub's ephemeral runner generates a fresh debug key per run, so
        // each APK has a different signature and Android refuses to install one over another
        // ("App not installed"). This is a self-distribution key, not a Play Store secret.
        create("zen") {
            storeFile = file("zen-release.keystore")
            storePassword = "zenblocker"
            keyAlias = "zen"
            keyPassword = "zenblocker"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("zen")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("zen")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)

  // Frosted-glass depth (RenderEffect on API 31+, graceful translucency fallback below)
  implementation(libs.haze)

  // Detection config + per-app guard settings are serialized as JSON
  implementation(libs.kotlinx.serialization.json)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
