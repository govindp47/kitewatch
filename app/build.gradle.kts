import java.util.Properties

plugins {
    alias(libs.plugins.kitewatch.android.application)
    alias(libs.plugins.kitewatch.android.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // --- START MODIFICATION ---
    // [FM-001] Apply Google Services plugin for google-services.json processing
    alias(libs.plugins.google.services)
    // --- END MODIFICATION ---
}

// Load secrets.properties — falls back gracefully to empty if absent (CI injects via env)
val secretsFile = rootProject.file("secrets.properties")
val secrets =
    Properties().apply {
        if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
    }

fun secret(key: String): String =
    secrets.getProperty(key)
        ?: System.getenv(key)
        ?: ""

android {
    namespace = "com.kitewatch.app"

    defaultConfig {
        applicationId = "com.kitewatch.app"
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "KITE_API_KEY", "\"${secret("KITE_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"${secret("GOOGLE_OAUTH_CLIENT_ID")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = secrets
                .getProperty("KEYSTORE_PATH")
                ?.let { rootProject.file(it) }
                ?: rootProject.file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = secret("KEYSTORE_PASSWORD")
            keyAlias = secret("KEY_ALIAS")
            keyPassword = secret("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("String", "BUILD_VARIANT", "\"debug\"")
            // --- START MODIFICATION ---
            // [FM-003] Firebase migration feature flags — false until Phase 2 validation
            buildConfigField("Boolean", "FEATURE_FIREBASE_AUTH", "false")
            buildConfigField("Boolean", "FEATURE_FIREBASE_PROXY", "false")
            // --- END MODIFICATION ---
        }
        create("staging") {
            applicationIdSuffix = ".staging"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BUILD_VARIANT", "\"staging\"")
            // Library modules only publish debug/release; fall back to release for staging.
            matchingFallbacks += "release"
            // --- START MODIFICATION ---
            // [FM-003] Firebase migration feature flags — updated to true in FM-013 post-OAuth validation
            buildConfigField("Boolean", "FEATURE_FIREBASE_AUTH", "false")
            buildConfigField("Boolean", "FEATURE_FIREBASE_PROXY", "false")
            // --- END MODIFICATION ---
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BUILD_VARIANT", "\"release\"")
            // --- START MODIFICATION ---
            // [FM-003] Firebase migration feature flags — updated to true in FM-038 post-final validation
            buildConfigField("Boolean", "FEATURE_FIREBASE_AUTH", "false")
            buildConfigField("Boolean", "FEATURE_FIREBASE_PROXY", "false")
            // --- END MODIFICATION ---
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Feature modules
    implementation(project(":feature:portfolio"))
    implementation(project(":feature:holdings"))
    implementation(project(":feature:orders"))
    implementation(project(":feature:transactions"))
    implementation(project(":feature:gtt"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:auth"))

    // Core modules
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))

    // Infra modules
    implementation(project(":infra:worker"))
    implementation(project(":infra:auth"))
    implementation(project(":infra:backup"))
    implementation(project(":infra:csv"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.compose.activity)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.timber)

    // --- START MODIFICATION ---
    // [FM-001] Firebase BOM and dependencies
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.functions.ktx)
    // --- END MODIFICATION ---

    // --- START MODIFICATION ---
    // [FM-002] infra-firebase module
    implementation(project(":infra:firebase"))
    // --- END MODIFICATION ---

    testImplementation(libs.junit)
}
