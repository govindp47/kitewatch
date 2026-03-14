import java.util.Properties

plugins {
    alias(libs.plugins.kitewatch.android.library)
    alias(libs.plugins.kitewatch.android.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Load secrets from local secrets.properties (gitignored).
// CI injects these as environment variables; fall back to an empty string so
// the module still compiles in environments without a secrets file.
private val secretsFile = rootProject.file("secrets.properties")
private val secrets =
    Properties().apply {
        if (secretsFile.exists()) load(secretsFile.inputStream())
    }

android {
    namespace = "com.kitewatch.feature.onboarding"

    buildFeatures.buildConfig = true

    defaultConfig {
        buildConfigField(
            "String",
            "KITE_API_SECRET",
            "\"${secrets.getProperty("KITE_API_SECRET", "")}\"",
        )
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":infra:auth"))
    implementation(project(":core:ui"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.browser)
    implementation(libs.compose.activity)
    implementation(libs.lifecycle.runtime.ktx)
}
