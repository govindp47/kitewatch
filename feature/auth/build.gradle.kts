plugins {
    alias(libs.plugins.kitewatch.android.library)
    alias(libs.plugins.kitewatch.android.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kitewatch.feature.auth"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":infra:auth"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.biometric)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
}
